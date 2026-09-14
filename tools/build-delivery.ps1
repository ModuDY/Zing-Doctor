<#
.SYNOPSIS
  zing-doctor 交付包打包脚本（Windows / PowerShell）

.DESCRIPTION
  Full：全量包，含 tools/docker-compose 离线二进制（118 MB，无外网服务器用）
  Lite：精简包，不含离线 compose 二进制（服务器已装 docker compose 用，约 29 MB）

.PARAMETER Lite    生成精简包（默认全量）
.PARAMETER Build   打包前先构建：mvn package（后端）+ vite build（前端）
.PARAMETER OutDir  输出目录，默认项目根

.EXAMPLE
  .\tools\build-delivery.ps1 -Lite
  .\tools\build-delivery.ps1 -Build -Lite
#>
param(
    [switch]$Lite,
    [switch]$Build,
    [string]$OutDir
)

$ErrorActionPreference = 'Stop'

# ---------- 原生命令容错（关键，勿删） ----------
# 本脚本要调用原生命令（mvn / node）。PowerShell 的坑：原生命令往 stderr 写内容时，
# 在 $ErrorActionPreference='Stop' 下会被**当成脚本错误直接抛出**，
# 连 $LASTEXITCODE 都来不及判断。这不是理论风险 —— Vite 每次构建都会往 stderr 打一条
# "The CJS build of Vite's Node API is deprecated"，表现为「构建明明成功，打包却中断」。
#
# PS 7.3+ 用这个开关关闭该行为；Windows PowerShell 5.1 没有这个变量，
# 由下面的 Invoke-Native 兜底（临时降级 EAP，并只用 exit code 判断成败）。
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Invoke-Native {
    param([string]$Label, [string]$Exe, [string[]]$ExeArgs)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # 2>&1 把 stderr 合并进 stdout：既保住进度输出，也避免被当成错误抛出
        & $Exe @ExeArgs 2>&1 | ForEach-Object { Write-Host $_ }
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($LASTEXITCODE -ne 0) { throw "$Label 失败（exit=$LASTEXITCODE）" }
}

$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = $root }

# ---------- 工具链探测：优先项目自带 .tools/，其次 PATH ----------
# .tools/ 为本地打包工具链（Node/Maven，已在 .gitignore），换机器后脚本仍可用
$node = $null
$nodeDir = Get-ChildItem "$root\.tools" -Directory -Filter 'node-v*-win-x64' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($nodeDir -and (Test-Path (Join-Path $nodeDir.FullName 'node.exe'))) {
    $node = Join-Path $nodeDir.FullName 'node.exe'
} else {
    $cmd = Get-Command node -ErrorAction SilentlyContinue
    if ($cmd) { $node = $cmd.Source }
}

$mvn = 'mvn'
$mvnArgs = @()
$mvnDir = Get-ChildItem "$root\.tools" -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($mvnDir) {
    $mvn = Join-Path $mvnDir.FullName 'bin\mvn.cmd'
    if (Test-Path "$root\.tools\settings.xml") {
        $mvnArgs += @('-s', "$root\.tools\settings.xml")
    }
}
# mvn 依赖 JAVA_HOME；未设置时从 PATH 上的 java 反推
if (-not $env:JAVA_HOME) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaCmd.Source) }
}

# ---------- 可选：先构建 ----------
if ($Build) {
    Write-Host ">>> 构建后端：$mvn package" -ForegroundColor Cyan
    Invoke-Native -Label '后端构建' -Exe $mvn -ExeArgs (@($mvnArgs) + @('-f', "$root\pom.xml", 'package', '-DskipTests', '-q'))

    Write-Host '>>> 构建前端 vite build' -ForegroundColor Cyan
    if (-not $node) { throw '未找到 node，请先安装或手动构建前端' }
    # 用调用运算符同步执行：Start-Process 在本机环境下会在 vite 写盘阶段异常中断构建
    Push-Location "$root\frontend"
    try {
        Invoke-Native -Label '前端构建' -Exe $node -ExeArgs @('node_modules/vite/bin/vite.js', 'build')
    } finally {
        Pop-Location
    }
}

# ---------- 同步 jar 到交付约定位置 ----------
$jar = "$root\target\zing-doctor.jar"
if (-not (Test-Path $jar)) { throw "未找到 $jar ，请先构建后端" }
New-Item -ItemType Directory -Force -Path "$root\app" | Out-Null
Copy-Item $jar "$root\app\zing-doctor.jar" -Force
Write-Host '>>> 已同步 jar -> app/zing-doctor.jar' -ForegroundColor Cyan

# ---------- 组装 ----------
$stamp = Get-Date -Format 'yyyyMMdd'
if ($Lite) { $suffix = 'lite' } else { $suffix = 'full' }
$zip = Join-Path $OutDir ("zing-doctor-deploy-$stamp-$suffix.zip")
$stage = Join-Path $env:TEMP ('zing-pkg-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$work = Join-Path $stage 'zing-doctor'
New-Item -ItemType Directory -Force -Path $work | Out-Null
Write-Host (">>> 组装到 " + $work) -ForegroundColor Cyan

Copy-Item "$root\app" (Join-Path $work 'app') -Recurse -Force
Copy-Item "$root\sql" (Join-Path $work 'sql') -Recurse -Force
Copy-Item "$root\docs" (Join-Path $work 'docs') -Recurse -Force
Copy-Item "$root\lib" (Join-Path $work 'lib') -Recurse -Force
Copy-Item "$root\docker-compose.yml" $work -Force
Copy-Item "$root\Dockerfile" $work -Force
Copy-Item "$root\install.sh" $work -Force
Copy-Item "$root\README.md" $work -Force

$fe = Join-Path $work 'frontend'
New-Item -ItemType Directory -Force -Path $fe | Out-Null
Copy-Item "$root\frontend\dist" (Join-Path $fe 'dist') -Recurse -Force
Copy-Item "$root\frontend\nginx.conf" $fe -Force
Copy-Item "$root\frontend\Dockerfile" $fe -Force

$tools = Join-Path $work 'tools'
New-Item -ItemType Directory -Force -Path (Join-Path $tools 'db-init') | Out-Null
Copy-Item "$root\tools\db-init\*" (Join-Path $tools 'db-init') -Force
if (Test-Path "$root\tools\db-init-classes") {
    New-Item -ItemType Directory -Force -Path (Join-Path $tools 'db-init-classes') | Out-Null
    Copy-Item "$root\tools\db-init-classes\*" (Join-Path $tools 'db-init-classes') -Force
}
if (-not $Lite) {
    Copy-Item "$root\tools\docker-compose" (Join-Path $tools 'docker-compose') -Recurse -Force
}

# ---------- 部署说明 ----------
$formDesc = 'Full：含离线 docker-compose 二进制（tools/docker-compose），适用于无外网服务器'
if ($Lite) { $formDesc = 'Lite：不含离线 docker-compose 二进制，服务器需已安装 docker compose' }

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add('# zing-doctor 交付包')
[void]$lines.Add('')
[void]$lines.Add('生成时间：' + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
[void]$lines.Add('形态：' + $formDesc)
[void]$lines.Add('')
[void]$lines.Add('解压后顶层目录就是 zing-doctor/（可直接覆盖上一次的部署目录）。')
[void]$lines.Add('')
[void]$lines.Add('## 目录')
[void]$lines.Add('- app/zing-doctor.jar   后端可执行 jar（JDK 8+，8081，已含达梦驱动）')
[void]$lines.Add('- frontend/dist/        前端静态产物')
[void]$lines.Add('- frontend/nginx.conf   /api、/entry 反代 + history 回退')
[void]$lines.Add('- sql/                  达梦 DM8 建表与种子脚本（按序号执行）')
[void]$lines.Add('- tools/db-init/        数据库初始化工具')
[void]$lines.Add('- lib/                  达梦 JDBC 驱动')
[void]$lines.Add('- docs/                 架构、外链规范、部署手册、产品设计')
[void]$lines.Add('')
[void]$lines.Add('## 全新部署（在部署目录的父目录执行，例如 /data）')
[void]$lines.Add('```')
[void]$lines.Add('cd /data')
[void]$lines.Add('unzip -o zing-doctor-deploy-<日期>-lite.zip     # 解出 /data/zing-doctor')
[void]$lines.Add('cd zing-doctor')
[void]$lines.Add('# 修改 docker-compose.yml：DOCTOR_URL / DOCTOR_USERNAME / DOCTOR_PASSWORD / EXTERNAL_LINK_BASE_URL')
[void]$lines.Add('bash install.sh                                 # 无外网走内网直连；有外网也可 docker compose up -d --build')
[void]$lines.Add('```')
[void]$lines.Add('')
[void]$lines.Add('## 增量更新（推荐：整包覆盖，一步到位）')
[void]$lines.Add('在部署目录的【父目录】执行，解压会自动覆盖 zing-doctor/ 里的旧文件：')
[void]$lines.Add('```')
[void]$lines.Add('cd /data                       # 部署目录 /data/zing-doctor 的父目录')
[void]$lines.Add('unzip -o zing-doctor-deploy-<日期>-lite.zip')
[void]$lines.Add('cd zing-doctor && bash install.sh')
[void]$lines.Add('```')
[void]$lines.Add('只想零星替换文件时：')
[void]$lines.Add('```')
[void]$lines.Add('cp -f <新包>/app/zing-doctor.jar <部署目录>/app/zing-doctor.jar')
[void]$lines.Add('cp -r <新包>/frontend/dist/. <部署目录>/frontend/dist/    # 覆盖目录内容')
[void]$lines.Add('rm -f <部署目录>/backend.pid && bash <部署目录>/install.sh')
[void]$lines.Add('```')
[void]$lines.Add('')
[void]$lines.Add('## 常见坑')
[void]$lines.Add('- 前端 dist 是 bind mount：**不要 `rm -rf frontend/dist`**（目录 inode 变化后容器仍挂旧空目录，表现为 403/500 或页面不更新），要用覆盖内容的方式。')
[void]$lines.Add('- **不要在部署目录内部解压**新包（会多出一层 zing-doctor/zing-doctor，install.sh 部署的还是里面那份）。')
[void]$lines.Add('- 不要整目录覆盖 docker-compose.yml（会还原你改过的达梦地址 / EXTERNAL_LINK_BASE_URL）。')
[void]$lines.Add('- 更新后核对版本：`docker exec zing-doctor-frontend grep -o "SofaScore-[A-Za-z0-9_-]*\.js" /usr/share/nginx/html/index.html`')
[void]$lines.Add('- 浏览器仍显示旧页面时先 Ctrl+F5：index.html 已配 no-cache，正常发版即可生效。')
[IO.File]::WriteAllLines((Join-Path $work 'DEPLOY.md'), $lines, (New-Object Text.UTF8Encoding($false)))

# ---------- 行尾归一化：CRLF -> LF ----------
# ⚠️ Windows 工作区里的 install.sh / docker-compose.yml / nginx.conf / *.sql 都是 CRLF。
# 直接打进 zip 后，Linux 上按 shebang 执行 install.sh 会报：
#   /usr/bin/env: “bash\r”: 没有那个文件或目录
# YAML/nginx 配置虽多数场景容忍 CRLF，但统一成 LF 最省事。
# 注意：jar / png 等二进制不动（按扩展名白名单处理）。
$lfExt = @('.sh', '.sql', '.yml', '.yaml', '.conf', '.md', '.java', '.properties', '.xml', '.txt', '.cfg', '.ini', '.env')
$lfFiles = Get-ChildItem $work -Recurse -File | Where-Object {
    ($lfExt -contains $_.Extension.ToLower()) -or ($_.Name -eq 'Dockerfile') -or ($_.Name -like 'Dockerfile.*')
}
$lfCount = 0
foreach ($f in $lfFiles) {
    $text = [IO.File]::ReadAllText($f.FullName)
    if ($text.Contains("`r`n") -or $text.Contains("`r")) {
        $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
        [IO.File]::WriteAllText($f.FullName, $text, (New-Object Text.UTF8Encoding($false)))
        $lfCount++
    }
}
Write-Host (">>> 行尾归一化（CRLF -> LF）：$lfCount 个文本文件") -ForegroundColor Cyan

# ---------- 压缩 ----------
# ⚠️ 条目必须以 zing-doctor/ 开头（以 $stage 为基准，而不是 $work）：
# 交付包解压出来应得到 zing-doctor/ 目录，用户在父目录 unzip 即可直接覆盖上次的部署目录，
# 然后 cd zing-doctor && bash install.sh。若剥掉这一层，用户在 /data 下解压会把文件散落到
# /data 根目录，而 install.sh 仍部署 /data/zing-doctor（旧目录）→ 每次"更新"都不生效。
# ⚠️ 不用 Compress-Archive：Windows 上它把条目名写成 "frontend\dist\index.html"（反斜杠），
# Linux 的 unzip 不把 "\" 当路径分隔符，会解出一个名为 "frontend\dist\index.html" 的单文件，
# 目录结构整体丢失（install.sh 随即报缺 frontend/dist/index.html）。这里显式用 "/" 写条目。
if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host '>>> 压缩中（条目统一用 / 分隔并保留 zing-doctor/ 顶层目录，兼容 Linux unzip）…' -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipArchive = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem $stage -Recurse -File | ForEach-Object {
        $entryName = $_.FullName.Substring($stage.Length + 1).Replace('\', '/')
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zipArchive, $_.FullName, $entryName, [IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $zipArchive.Dispose()
}
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue

$size = [math]::Round((Get-Item $zip).Length / 1MB, 2)
Write-Host (">>> 完成：" + $zip + " （" + $size + " MB）") -ForegroundColor Green

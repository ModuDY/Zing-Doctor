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
    & $mvn @mvnArgs -f "$root\pom.xml" package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }

    Write-Host '>>> 构建前端 vite build' -ForegroundColor Cyan
    if (-not $node) { throw '未找到 node，请先安装或手动构建前端' }
    $viteArgs = @('node_modules/vite/bin/vite.js', 'build')
    $proc = Start-Process -FilePath $node -ArgumentList $viteArgs -WorkingDirectory "$root\frontend" -Wait -PassThru -NoNewWindow
    if ($proc.ExitCode -ne 0) { throw '前端构建失败' }
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
[void]$lines.Add('## 目录')
[void]$lines.Add('- app/zing-doctor.jar   后端可执行 jar（JDK 8+，8081，已含达梦驱动）')
[void]$lines.Add('- frontend/dist/        前端静态产物')
[void]$lines.Add('- frontend/nginx.conf   /api、/entry 反代 + history 回退')
[void]$lines.Add('- sql/                  达梦 DM8 建表与种子脚本（按序号执行）')
[void]$lines.Add('- tools/db-init/        数据库初始化工具')
[void]$lines.Add('- lib/                  达梦 JDBC 驱动')
[void]$lines.Add('- docs/                 架构、外链规范、部署手册、产品设计')
[void]$lines.Add('')
[void]$lines.Add('## 全新部署')
[void]$lines.Add('```')
[void]$lines.Add('cd zing-doctor')
[void]$lines.Add('# 修改 docker-compose.yml：DOCTOR_URL / DOCTOR_USERNAME / DOCTOR_PASSWORD / EXTERNAL_LINK_BASE_URL')
[void]$lines.Add('docker compose up -d --build')
[void]$lines.Add('```')
[void]$lines.Add('')
[void]$lines.Add('## 增量更新（后端 jar 变了需重建镜像；前端为 volume 挂载，覆盖即可）')
[void]$lines.Add('```')
[void]$lines.Add('cp -f app/zing-doctor.jar <部署目录>/app/zing-doctor.jar')
[void]$lines.Add('rm -rf <部署目录>/frontend/dist && cp -r frontend/dist <部署目录>/frontend/')
[void]$lines.Add('docker compose build backend && docker compose up -d backend && docker compose restart frontend')
[void]$lines.Add('```')
[void]$lines.Add('注意：不要整目录覆盖，会还原已修改的 docker-compose.yml。')
[IO.File]::WriteAllLines((Join-Path $work 'DEPLOY.md'), $lines, (New-Object Text.UTF8Encoding($false)))

# ---------- 压缩 ----------
# ⚠️ 不用 Compress-Archive：Windows 上它把条目名写成 "frontend\dist\index.html"（反斜杠），
# Linux 的 unzip 不把 "\" 当路径分隔符，会解出一个名为 "frontend\dist\index.html" 的单文件，
# 目录结构整体丢失（install.sh 随即报缺 frontend/dist/index.html）。这里显式用 "/" 写条目。
if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host '>>> 压缩中（条目统一用 / 分隔，兼容 Linux unzip）…' -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipArchive = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem $work -Recurse -File | ForEach-Object {
        $entryName = $_.FullName.Substring($work.Length + 1).Replace('\', '/')
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zipArchive, $_.FullName, $entryName, [IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $zipArchive.Dispose()
}
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue

$size = [math]::Round((Get-Item $zip).Length / 1MB, 2)
Write-Host (">>> 完成：" + $zip + " （" + $size + " MB）") -ForegroundColor Green

<#
.SYNOPSIS
  zing-doctor 交付包打包脚本（Windows / PowerShell）

.DESCRIPTION
  Full：全量包，含 tools/docker-compose 离线二进制（118 MB，无外网服务器用）
  Lite：精简包，不含离线 compose 二进制（服务器已装 docker compose 用，约 29 MB）

.PARAMETER Lite     生成精简包（默认全量：含离线 docker-compose 二进制）
.PARAMETER Build    打包前先构建：mvn package（后端）+ vite build（前端）
.PARAMETER KeepDocs 额外打进 docs/ 全部与 README.md（内部交付用）
                    默认只带部署方真正用得到的 02/03/04 三份文档，产品设计、设计稿、原型一律不进包
.PARAMETER Sanitize 脱敏：把 docker-compose.yml 与 jar 内 application.yml 的出厂口令/密钥置为 CHANGE_ME
                    ⚠️ 启用后部署方必须自行填写真实口令，否则 install.sh 数据库初始化与后端连库都会失败
.PARAMETER OutDir   输出目录，默认项目根

.EXAMPLE
  .\tools\build-delivery.ps1 -Lite
  .\tools\build-delivery.ps1 -Build -Lite
  .\tools\build-delivery.ps1 -Build -Lite -Sanitize    # 外发给院方前建议加 -Sanitize
#>
param(
    [switch]$Lite,
    [switch]$Build,
    [switch]$KeepDocs,
    [switch]$Sanitize,
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
    # 先跑单元测试再构建，失败即中止打包（Invoke-Native 非 0 会 throw）。
    # 这不是形式主义：曾经「改了构造签名没同步测试」在本地打包全程无感 ——
    # package -DskipTests 连测试代码都不编译 —— 直到 CI 上 test-compile 才红。
    # 那批测试不依赖数据库，几十秒跑完，放在打包前拦一道性价比很高。
    Write-Host ">>> 自检：跑单元测试（失败即中止打包）" -ForegroundColor Cyan
    Invoke-Native -Label '单元测试' -Exe $mvn -ExeArgs (@($mvnArgs) + @('-f', "$root\pom.xml", 'test', '-B'))

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

# ---------- 编译数据库初始化工具 ----------
# ⚠️ tools/db-init/DbInit.class 是预编译产物：源码改了却没重新编译，服务器上跑的
# 就还是旧逻辑（曾因此把 PL/SQL 块按分号切碎 → 13/14 初始化脚本报「语法分析出错」）。
# 目标字节码必须是 Java 8（服务器 JDK 8），故显式 -source/-target 1.8。
$javacCmd = Get-Command javac -ErrorAction SilentlyContinue
if ($javacCmd) {
    Write-Host '>>> 编译 tools/db-init/DbInit.java（Java 8 字节码）' -ForegroundColor Cyan
    Invoke-Native -Label 'DbInit 编译' -Exe $javacCmd.Source `
        -ExeArgs @('-source', '1.8', '-target', '1.8', '-encoding', 'UTF-8', '-d', "$root\tools\db-init", "$root\tools\db-init\DbInit.java")
} elseif (Test-Path "$root\tools\db-init\DbInit.class") {
    Write-Host '>>> 未找到 javac，沿用已有的 tools/db-init/DbInit.class' -ForegroundColor Yellow
} else {
    throw '未找到 javac，且 tools/db-init/DbInit.class 不存在：无法生成数据库初始化工具'
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
# 脱敏包单独命名：默认包（出厂口令，内部/现场直接用）与外发包（CHANGE_ME）不混用
if ($Sanitize) { $suffix = "$suffix-sanitized" }
$zip = Join-Path $OutDir ("zing-doctor-deploy-$stamp-$suffix.zip")
$stage = Join-Path $env:TEMP ('zing-pkg-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$work = Join-Path $stage 'zing-doctor'
New-Item -ItemType Directory -Force -Path $work | Out-Null
Write-Host (">>> 组装到 " + $work) -ForegroundColor Cyan

# ⚠️ 白名单原则：只放「运行 + 部署」必需的东西。
# 交付对象是医院信息科，包里不该出现：后端/前端源码、产品设计文档、设计稿与原型、
# 内部脚本（一次性生成脚本、校验脚本）、sourcemap。需要内部归档时用 -KeepDocs 打开。
Copy-Item "$root\app" (Join-Path $work 'app') -Recurse -Force
Copy-Item "$root\sql" (Join-Path $work 'sql') -Recurse -Force
Copy-Item "$root\lib" (Join-Path $work 'lib') -Recurse -Force
Copy-Item "$root\docker-compose.yml" $work -Force
Copy-Item "$root\Dockerfile" $work -Force
Copy-Item "$root\install.sh" $work -Force
# Debian/MySQL 直连部署脚本（不依赖 Docker 跑应用时使用），与 Docker 版 install.sh 并存
if (Test-Path "$root\install-mariadb-debian.sh") {
    Copy-Item "$root\install-mariadb-debian.sh" $work -Force
}
# 数据库连接配置文件：部署方改这里的 IP/端口/账号即可，不必再敲一长串环境变量
if (Test-Path "$root\conf") {
    New-Item -ItemType Directory -Force -Path (Join-Path $work 'conf') | Out-Null
    Copy-Item "$root\conf\*" (Join-Path $work 'conf') -Force
}

# ---------- 合成 MySQL/MariaDB 一次性初始化脚本 ----------
# 应用与库分两台机器时，应用机常常没有客户端；把 sql/mysql 下全部脚本合成一个文件，
# 部署方只需把这一份拷到数据库服务器执行一次（顺序与 install 脚本一致）。
$myDir = Join-Path $work 'sql\mysql'
if (Test-Path $myDir) {
    $myOrder = @(
        '00_init_user.sql', '00b_idempotent_helpers_doctor.sql', '24_fix_id_auto_increment.sql',
        '01_schema.sql', '02_seed.sql', '05_apache2_pdf.sql', '06_abx_drug_dict.sql',
        '07_sofa.sql', '08_sofa_p1.sql', '09_quality.sql', '10_quality_config.sql',
        '11_quality_count_rule.sql', '12_archive.sql', '13_auth.sql', '14_param_framework.sql',
        '15_quality_patient_fields.sql', '16_quality_fatality_ref.sql', '17_quality_rule_local.sql',
        '18_quality_manual_audit.sql', '19_quality_target_direction.sql',
        '20_quality_fact_patient_default_cols.sql', '21_ards_prone.sql',
        '22_ards_prone_config.sql', '23_ards_prone_sign_work_no.sql')
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('-- ============================================================')
    [void]$sb.AppendLine('-- zing-doctor MySQL/MariaDB 一次性初始化脚本（打包时自动合成，勿手工编辑）')
    [void]$sb.AppendLine('--')
    [void]$sb.AppendLine('-- 用法（在数据库服务器上执行一次即可）：')
    [void]$sb.AppendLine('--   mysql -uroot -p < install-all.sql')
    [void]$sb.AppendLine('--   容器里：docker exec -i <mysql容器> mysql -uroot -p<密码> < install-all.sql')
    [void]$sb.AppendLine('--   图形工具：整个文件粘贴执行（含 DELIMITER，需支持存储过程语法）')
    [void]$sb.AppendLine('--')
    [void]$sb.AppendLine('-- 内容顺序：建库 -> 幂等存储过程 -> 字段修复 -> 建表 + 种子数据')
    [void]$sb.AppendLine('-- 全部幂等，可重复执行；执行完再在应用机跑 ./install-mariadb-debian.sh --config-only')
    [void]$sb.AppendLine('-- 注意：本文件不含建账号授权，账号授权按 docs 或安装脚本提示另行执行')
    [void]$sb.AppendLine('-- ============================================================')
    [void]$sb.AppendLine('')
    foreach ($mf in $myOrder) {
        $mp = Join-Path $myDir $mf
        if (Test-Path $mp) {
            [void]$sb.AppendLine("-- >>>>>>>>>> $mf >>>>>>>>>> --")
            [void]$sb.AppendLine([IO.File]::ReadAllText($mp))
        } else {
            Write-Host ">>> 警告：缺少 $mf，未合成进 install-all.sql" -ForegroundColor Yellow
        }
    }
    [IO.File]::WriteAllText((Join-Path $myDir 'install-all.sql'), $sb.ToString(), (New-Object Text.UTF8Encoding($false)))
    Write-Host '>>> 已生成 sql/mysql/install-all.sql（一次性初始化脚本，交给库侧执行）' -ForegroundColor Cyan
}
# 数据库变更清单：install.sh 不会执行 SQL（容器无达梦客户端），故把清单放包根目录，
# 部署方解压第一眼就能看到，避免「代码更新了但表没改」导致页面直接 500。
if (Test-Path "$root\DATABASE-CHANGES.md") {
    Copy-Item "$root\DATABASE-CHANGES.md" $work -Force
}

# 文档：默认只给部署/对接真正用得到的三份（install.sh 的提示也指向 04）。
# 产品设计（05~17）、ARDS 原型 docs/ards-prone、设计稿 docs/quality-board-redesign、
# 指标映射 CSV 与一次性脚本，属内部资料，不进外发包。
$depDocs = Join-Path $work 'docs'
New-Item -ItemType Directory -Force -Path $depDocs | Out-Null
foreach ($d in @('02-外链传参规范.md', '03-部署说明.md', '04-安装部署手册.md')) {
    if (Test-Path "$root\docs\$d") { Copy-Item "$root\docs\$d" $depDocs -Force }
}
if ($KeepDocs) {
    Copy-Item "$root\docs\*" $depDocs -Recurse -Force
    Copy-Item "$root\README.md" $work -Force
}

$fe = Join-Path $work 'frontend'
New-Item -ItemType Directory -Force -Path $fe | Out-Null
Copy-Item "$root\frontend\dist" (Join-Path $fe 'dist') -Recurse -Force
Copy-Item "$root\frontend\nginx.conf" $fe -Force
Copy-Item "$root\frontend\Dockerfile" $fe -Force

# db-init 只带编译产物（.class）：DbInit.java 是源码，不进包。
# 服务器上 install.sh 直接用 .class；没有 .java 时它也不会尝试 javac（见 resolve_dbinit_cp）。
$tools = Join-Path $work 'tools'
New-Item -ItemType Directory -Force -Path (Join-Path $tools 'db-init') | Out-Null
Get-ChildItem "$root\tools\db-init" -File | Where-Object { $_.Extension -ne '.java' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $tools 'db-init') -Force }
if (Test-Path "$root\tools\db-init-classes") {
    New-Item -ItemType Directory -Force -Path (Join-Path $tools 'db-init-classes') | Out-Null
    Get-ChildItem "$root\tools\db-init-classes" -File | Where-Object { $_.Extension -ne '.java' } |
        ForEach-Object { Copy-Item $_.FullName (Join-Path $tools 'db-init-classes') -Force }
}
if (-not $Lite) {
    Copy-Item "$root\tools\docker-compose" (Join-Path $tools 'docker-compose') -Recurse -Force
}

# ---------- 可选：脱敏出厂口令 ----------
# 包里两处带出厂口令：docker-compose.yml（达梦口令、外链密钥）与 jar 内
# BOOT-INF/classes/application.yml 的默认口令（环境变量未注入时生效）。
# 内部部署无所谓；包一旦外发，等于把库口令和 JWT 密钥一起给了出去。
# ⚠️ 脱敏后部署方必须自己填口令，否则 install.sh 的数据库初始化与后端连库都会失败。
if ($Sanitize) {
    $compose = Join-Path $work 'docker-compose.yml'
    if (Test-Path $compose) {
        $t = [IO.File]::ReadAllText($compose)
        foreach ($k in @('DOCTOR_PASSWORD', 'EXTERNAL_LINK_SECRET', 'ICU_LINK_TOKEN')) {
            $t = [regex]::Replace($t, "(?m)^(\s*$k\s*:\s*).*$", '${1}CHANGE_ME')
        }
        [IO.File]::WriteAllText($compose, $t, (New-Object Text.UTF8Encoding($false)))
        Write-Host '>>> 已脱敏 docker-compose.yml（DOCTOR_PASSWORD / EXTERNAL_LINK_SECRET / ICU_LINK_TOKEN）' -ForegroundColor Yellow
    }

    # conf/db.conf 同样带出厂数据库口令与外链密钥
    $pkgConf = Join-Path $work 'conf\db.conf'
    if (Test-Path $pkgConf) {
        $ct = [IO.File]::ReadAllText($pkgConf)
        foreach ($k in @('DB_ADMIN_PASSWORD', 'APP_DB_PASSWORD', 'ICU_DB_PASSWORD', 'ICU_LINK_TOKEN', 'EXTERNAL_LINK_SECRET')) {
            $ct = [regex]::Replace($ct, "(?m)^(\s*$k\s*=\s*).*$", '${1}CHANGE_ME')
        }
        [IO.File]::WriteAllText($pkgConf, $ct, (New-Object Text.UTF8Encoding($false)))
        Write-Host '>>> 已脱敏 conf/db.conf（数据库口令 / 外链密钥置为 CHANGE_ME）' -ForegroundColor Yellow
    }

    $pkgJar = Join-Path $work 'app\zing-doctor.jar'
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem   # ZipFile 类在这个程序集里
    $za = [IO.Compression.ZipFile]::Open($pkgJar, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $entry = $za.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/application.yml' } | Select-Object -First 1
        if ($entry) {
            $sr = New-Object IO.StreamReader($entry.Open())
            $text = $sr.ReadToEnd()
            $sr.Close()
            $sb = New-Object System.Text.StringBuilder
            # 只动口令/密钥类的默认值（键名含 password / secret / token），host/port 等保持
            # 原样（改了反而让现场连不上）。只替换**非空**默认值：留空的项语义是「不启用」，
            # 填成 CHANGE_ME 会把它变成默认开启（如 config-write-token 留空 = 写接口不校验）。
            foreach ($line in ($text -split "`n")) {
                $l = $line
                if ($l -match '^\s*[\w-]*(password|secret|token)[\w-]*\s*:') {
                    $l = [regex]::Replace($l, '(\$\{[A-Z_]+:)([^}]+)(\})', '${1}CHANGE_ME${3}')
                }
                [void]$sb.AppendLine($l)
            }
            $entry.Delete()
            $ne = $za.CreateEntry('BOOT-INF/classes/application.yml', [IO.Compression.CompressionLevel]::Optimal)
            $sw = New-Object IO.StreamWriter($ne.Open())
            $sw.Write($sb.ToString())
            $sw.Close()
            Write-Host '>>> 已脱敏 jar 内 application.yml（口令 / 密钥默认值）' -ForegroundColor Yellow
        } else {
            Write-Host '>>> jar 内未找到 BOOT-INF/classes/application.yml，跳过 jar 脱敏' -ForegroundColor Yellow
        }
    } finally {
        $za.Dispose()
    }
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
[void]$lines.Add('## 口令与密钥（外发前必读）')
if ($Sanitize) {
    [void]$lines.Add('本包**已脱敏**：docker-compose.yml 的 `DOCTOR_PASSWORD` / `EXTERNAL_LINK_SECRET` / `ICU_LINK_TOKEN`，')
    [void]$lines.Add('以及 jar 内 application.yml 的出厂口令，均已置为 `CHANGE_ME`。')
    [void]$lines.Add('启动前**必须填写**（二选一）：')
    [void]$lines.Add('1. 改 docker-compose.yml 里的达梦口令 —— install.sh 也从这个文件取口令做数据库初始化；')
    [void]$lines.Add('2. 或注入环境变量：DM_DOCTOR_PASSWORD / DM_ICU_PASSWORD / EXTERNAL_LINK_SECRET / AUTH_JWT_SECRET / ADMIN_PASSWORD。')
} else {
    [void]$lines.Add('本包含**出厂默认口令**（docker-compose.yml 的 `DOCTOR_PASSWORD`、jar 内 application.yml 的默认口令）。')
    [void]$lines.Add('发给院方前，请重新打包并加 `-Sanitize`，或手工改成院方自己的口令。')
}
[void]$lines.Add('')
[void]$lines.Add('## ⚠️ 升级前先看：DATABASE-CHANGES.md')
[void]$lines.Add('')
[void]$lines.Add('应用容器里没有达梦客户端，install.sh **不会**自动执行 SQL。')
[void]$lines.Add('本次交付若含表结构变更，必须先人工在数据库上执行 **DATABASE-CHANGES.md** 里的语句，')
[void]$lines.Add('否则新代码打开页面直接 500（典型报错：`无效的列名[xxx]`）。')
[void]$lines.Add('执行完无需重启容器，刷新页面即可生效。')
[void]$lines.Add('')
[void]$lines.Add('解压后顶层目录就是 zing-doctor/（可直接覆盖上一次的部署目录）。')
[void]$lines.Add('')
[void]$lines.Add('## 目录')
[void]$lines.Add('- **DATABASE-CHANGES.md** 数据库变更清单（升级先看，含本次需手工执行的 SQL）')
[void]$lines.Add('- conf/db.conf         数据库连接配置（库地址/端口/账号/应用目录 APP_HOME；install-mariadb-debian.sh 读取）')
[void]$lines.Add('- app/zing-doctor.jar   后端可执行 jar（JDK 8+，8081，已含达梦驱动）')
[void]$lines.Add('- frontend/dist/        前端静态产物')
[void]$lines.Add('- frontend/nginx.conf   /api、/entry 反代 + history 回退')
[void]$lines.Add('- sql/                  达梦 DM8 建表与种子脚本（按序号执行）')
[void]$lines.Add('- tools/db-init/        数据库初始化工具')
[void]$lines.Add('- sql/mysql/            MySQL / MariaDB 建表脚本（Debian 直连部署用，达梦环境忽略）')
[void]$lines.Add('  - sql/mysql/install-all.sql  一次性初始化（库在另一台机器时，拷这一份过去执行）')
[void]$lines.Add('- install.sh            Docker 部署脚本（默认，达梦 DM8）')
[void]$lines.Add('- install-mariadb-debian.sh  Debian 直连部署脚本（MySQL / MariaDB，不使用 Docker 跑应用时用）')
if ($KeepDocs) {
    [void]$lines.Add('- docs/                 全部文档（含产品设计，内部交付包）')
} else {
    [void]$lines.Add('- docs/                 部署必需文档：外链传参规范、部署说明、安装部署手册')
}
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
[void]$lines.Add('- **表结构变更必须手工执行 SQL**：见 DATABASE-CHANGES.md。容器里没有 disql，install.sh 不会代跑；漏执行的表现为：相关页面打开即 500（`无效的列名[xxx]`）。')
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

# ---------- 出库自检：源码 / 内部产物一律不许进包 ----------
# 有了这道闸，以后谁往组装清单里加了东西，打包会直接失败，而不是等包发到医院才发现。
$badExt = @('.java', '.map', '.ts', '.scss', '.less', '.py', '.design', '.ps1', '.jsx', '.tsx')
$badNames = @('package.json', 'package-lock.json', 'pom.xml', 'vite.config.js', '.gitignore', '.env')
$bad = New-Object System.Collections.ArrayList
Get-ChildItem $work -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($work.Length + 1)
    if ($badExt -contains $_.Extension.ToLower()) {
        [void]$bad.Add("$rel（源码 / 构建中间产物）")
    } elseif ($_.Name -like '*.vue') {
        [void]$bad.Add("$rel（前端源码）")
    } elseif ($badNames -contains $_.Name) {
        [void]$bad.Add("$rel（构建配置 / 环境文件）")
    } elseif ($rel -match '(^|\\)(node_modules|target|\.git|\.vite|\.codebuddy|\.idea|src)(\\|$)') {
        [void]$bad.Add("$rel（目录黑名单）")
    }
}
if ($bad.Count -gt 0) {
    Write-Host '>>> 出库自检未通过，包内出现不该外发的内容：' -ForegroundColor Red
    $bad | Select-Object -First 20 | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
    throw '打包中止：请从组装清单中剔除上述内容'
}
Write-Host '>>> 出库自检通过：无源码 / sourcemap / 内部构建产物' -ForegroundColor Cyan

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
if (-not $Sanitize) {
    Write-Host '>>> 提示：包内仍为出厂默认口令（docker-compose.yml 的 DOCTOR_PASSWORD 与 jar 内 application.yml）。' -ForegroundColor Yellow
    Write-Host '    外发给院方前请加 -Sanitize 重新打包，或手工改成院方自己的口令。' -ForegroundColor Yellow
}

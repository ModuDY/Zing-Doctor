<#
.SYNOPSIS
  一键交付：测试 → 打包 → 提交 → 推送

.DESCRIPTION
  把日常交付的四步串成一条命令。省事是次要的，它主要防两类真实踩过的坑：

  1) 打包前没跑测试。曾经「改了构造签名没同步测试」在本地打包全程无感 ——
     package -DskipTests 连测试代码都不编译 —— 直到 CI 上 test-compile 才红，
     那时包已经交出去了。build-delivery.ps1 -Build 内部已经会跑 mvn test，
     本脚本无条件带 -Build，测试不过就中止，不会带着红测试继续出包。

  2) 打包要几分钟，期间如果又改了文件，提交会把这些「打包后才出现」的改动
     一起卷进去 —— 那是没经过测试的半截代码，而且混在一个提交里很难事后拆。
     所以脚本一开始就锁定待提交清单，最后只提交这批；打包后新出现的改动
     不提交，只在结尾列出来提醒。

.PARAMETER Message
  提交信息，支持多行（用 `n 换行）。不给则打开 git 编辑器。

.PARAMETER NoCommit
  只做「测试 + 打包」，不提交不推送。想先看产物时用。

.PARAMETER NoPush
  提交但不推送，便于推送前再检查一遍。

.PARAMETER Full
  全量包（含离线 docker-compose 二进制，约 118 MB）。默认精简包（约 36 MB）。

.PARAMETER KeepDocs
  额外打进 docs/ 全部与 README.md。默认只带部署方用得到的 02/03/04 三份。

.PARAMETER Sanitize
  脱敏：出厂口令 / 密钥置为 CHANGE_ME。外发院方前建议加。

.PARAMETER OutDir
  输出目录，默认项目根。

.EXAMPLE
  .\tools\ship.ps1 -Message "fix(workbench): 修正科室边界判定"

.EXAMPLE
  .\tools\ship.ps1 -Message "release: 9 月版本" -Full -Sanitize

.EXAMPLE
  .\tools\ship.ps1 -NoCommit
#>
param(
    [string]$Message,
    [switch]$NoCommit,
    [switch]$NoPush,
    [switch]$Full,
    [switch]$KeepDocs,
    [switch]$Sanitize,
    [string]$OutDir
)

$ErrorActionPreference = 'Stop'

# 与 build-delivery.ps1 同一个坑：原生命令往 stderr 写内容时，在 EAP=Stop 下会被
# 当成脚本错误直接抛出，连 $LASTEXITCODE 都来不及判断。Vite 每次构建都会往 stderr
# 打一条 CJS deprecation，表现为「构建明明成功、脚本却中断」。
$PSNativeCommandUseErrorActionPreference = $false

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# git status --porcelain 的一行 -> 路径。
# 重命名的行形如「R  old -> new」，取新名；路径含特殊字符时 git 会加引号，去掉。
#
# ⚠️ 本文件必须保存为「UTF-8 带 BOM」。Windows PowerShell 5.1 读取无 BOM 的 .ps1
# 时按系统 ANSI 代码页（GBK）解码，文件里的中文会被解成错字节并破坏引号配对，
# 表现为一堆离真正病因很远的「意外的 }」，极难排查（本脚本踩过：报错行号全在
# if/else 上，真因却是编码）。build-delivery.ps1 一直正常就是因为它带 BOM。
# 改动本文件后若报出莫名其妙的语法错误，先查 BOM 还在不在。
function Get-ChangedPaths {
    $raw = & git status --porcelain
    $out = New-Object System.Collections.ArrayList
    foreach ($line in $raw) {
        if ($line.Length -lt 4) { continue }
        $xy = $line.Substring(0, 2)
        $p = $line.Substring(3)
        if ($xy.IndexOf('R') -ge 0) {
            $idx = $p.IndexOf(' -> ')
            if ($idx -ge 0) { $p = $p.Substring($idx + 4) }
        }
        $p = $p.Trim().Trim([char]34)
        [void]$out.Add($p)
    }
    return @($out.ToArray())
}

Write-Host '仓库：' $root -ForegroundColor DarkGray

# ---------- 1/4 前置检查 ----------
Write-Host ''
Write-Host '[1/4] 前置检查' -ForegroundColor Cyan
$branchRaw = & git rev-parse --abbrev-ref HEAD
$branch = ([string]$branchRaw).Trim()
Write-Host ('  分支：' + $branch)

# 锁清单必须在打包之前：之后新增的改动就是「打包后才出现」的那批，不能提交
$before = Get-ChangedPaths
if ($before.Count -eq 0) {
    Write-Host '  工作区无改动 —— 将只做测试与打包，跳过提交与推送' -ForegroundColor Yellow
}
if ($before.Count -gt 0) {
    Write-Host ('  待提交 ' + $before.Count + ' 项（清单已锁定）：')
    foreach ($p in $before) { Write-Host ('    - ' + $p) -ForegroundColor DarkGray }
}

# ---------- 2/4 测试 + 打包 ----------
Write-Host ''
Write-Host '[2/4] 测试 + 打包（build-delivery.ps1 -Build，内部会先跑 mvn test）' -ForegroundColor Cyan

# 必须用哈希表 splatting。写成数组（@('-Build','-Lite')）时，元素只会被当成
# 位置参数，而 build-delivery.ps1 按位置第一个吃的是 [string]$OutDir ——
# 结果 -Build 被塞进 OutDir，打包去写 <根>/-Build/xxx.zip 直接失败。
$bdArgs = @{ Build = $true }
if (-not $Full) { $bdArgs['Lite'] = $true }
if ($KeepDocs) { $bdArgs['KeepDocs'] = $true }
if ($Sanitize) { $bdArgs['Sanitize'] = $true }
if ($OutDir) { $bdArgs['OutDir'] = $OutDir }

$log = Join-Path $env:TEMP 'ship-build.log'
$bd = Join-Path $PSScriptRoot 'build-delivery.ps1'
& $bd @bdArgs *> $log
$buildExit = $LASTEXITCODE
if ($buildExit -ne 0) {
    Write-Host ''
    Write-Host '  打包失败，未提交任何内容。' -ForegroundColor Red
    Write-Host ('  日志：' + $log) -ForegroundColor DarkGray
    Select-String -Path $log -Pattern '\[ERROR\]|Exception|Tests run:.*(Failures: [1-9]|Errors: [1-9])' -Encoding UTF8 |
        Select-Object -First 15 | ForEach-Object { Write-Host ('    ' + $_.Line.Trim()) -ForegroundColor Red }
    exit 1
}
Write-Host '  测试通过、打包完成' -ForegroundColor Green

if ($NoCommit) {
    Write-Host ''
    Write-Host '[3/4] 提交：按 -NoCommit 跳过' -ForegroundColor DarkGray
    Write-Host '[4/4] 推送：跳过' -ForegroundColor DarkGray
    exit 0
}

# ---------- 3/4 提交 ----------
Write-Host ''
Write-Host '[3/4] 提交' -ForegroundColor Cyan

$after = Get-ChangedPaths
$toCommit = @()
foreach ($p in $before) { if ($after -contains $p) { $toCommit += $p } }
$newFiles = @()
foreach ($p in $after) { if ($before -notcontains $p) { $newFiles += $p } }

if ($newFiles.Count -gt 0) {
    Write-Host '  打包期间出现了新改动，本次不提交（未经过测试）：' -ForegroundColor Yellow
    foreach ($p in $newFiles) { Write-Host ('      ! ' + $p) -ForegroundColor Yellow }
}

if ($toCommit.Count -gt 0) {
    & git add -- $toCommit
    if ($LASTEXITCODE -ne 0) { Write-Host '  git add 失败，中止' -ForegroundColor Red; exit 1 }

    # 提交信息普遍是多行且含中文，写文件再 -F 最稳
    if ($Message.Length -gt 0) {
        $msgFile = Join-Path $env:TEMP 'ship-msg.txt'
        [IO.File]::WriteAllText($msgFile, $Message, (New-Object Text.UTF8Encoding($false)))
        & git commit -F $msgFile --quiet
        $commitExit = $LASTEXITCODE
        Remove-Item $msgFile -Force
    }
    if ($Message.Length -eq 0) {
        & git commit
        $commitExit = $LASTEXITCODE
    }
    if ($commitExit -ne 0) {
        Write-Host '  提交失败。若卡在编辑器，改用 -Message 传入提交信息' -ForegroundColor Red
        exit 1
    }
    $c = & git log -1 --format='%h %s'
    Write-Host ('  ' + $c) -ForegroundColor Green
}
if ($toCommit.Count -eq 0) {
    Write-Host '  无文件需要提交' -ForegroundColor DarkGray
}

# ---------- 4/4 推送 ----------
if ($NoPush) {
    Write-Host ''
    Write-Host '[4/4] 推送：按 -NoPush 跳过' -ForegroundColor DarkGray
}
if (-not $NoPush) {
    Write-Host ''
    Write-Host '[4/4] 推送' -ForegroundColor Cyan
    # 这条推送链路偶发 early EOF / RPC failed，重试几次基本都能过
    $ok = $false
    $i = 1
    while (($i -le 3) -and (-not $ok)) {
        # git 把推送进度写进 stderr，在 EAP=Stop 下会被当成脚本错误直接抛出 ——
        # 表现为「推送其实成功了，脚本却报错退出」。临时降级，只用退出码判断成败，
        # 与 build-delivery.ps1 的 Invoke-Native 是同一个坑、同一种解法。
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & git -c http.version=HTTP/1.1 push Zing-Doctor $branch 2>&1 | Out-Null
        $pushExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($pushExit -eq 0) { $ok = $true }
        if (-not $ok) {
            Write-Host ('  第 ' + $i + ' 次失败，5 秒后重试…') -ForegroundColor Yellow
            Start-Sleep -Seconds 5
        }
        $i = $i + 1
    }
    if ($ok) { Write-Host '  已推送' -ForegroundColor Green }
    if (-not $ok) {
        Write-Host '  推送失败（提交已在本地）。网络恢复后手动：' -ForegroundColor Red
        Write-Host ('    git push Zing-Doctor ' + $branch) -ForegroundColor DarkGray
        exit 1
    }
}

# ---------- 收尾 ----------
Write-Host ''
Write-Host '完成。' -ForegroundColor Cyan
$left = Get-ChangedPaths
if ($left.Count -gt 0) {
    Write-Host '仍有未提交改动（本次刻意未纳入）：' -ForegroundColor Yellow
    foreach ($p in $left) { Write-Host ('  - ' + $p) -ForegroundColor Yellow }
    Write-Host '确认无误后，再跑一次本命令即可提交它们。' -ForegroundColor DarkGray
}

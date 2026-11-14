$ErrorActionPreference = 'Continue'
$git = 'D:\PortableGit\cmd\git.exe'
$src = 'D:\DEBUG\apk\sysui'
$tmp = Join-Path $env:TEMP 'aio_xtc_repo'
$repo = 'https://github.com/hszlk/AIO_XTC_systemui.git'

$tok = [System.IO.File]::ReadAllText((Join-Path $env:TEMP 'gh_secret.txt')).Trim()
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("hszlk:$tok"))
$authOpt = "http.https://github.com/.extraheader=AUTHORIZATION: basic $b64"
$env:GIT_TERMINAL_PROMPT = '0'

function Git([string[]]$argv) {
    & $git -c $authOpt @argv 2>&1
}

Write-Host '=== 1) clone ==='
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
Git @('clone', '--quiet', $repo, $tmp) | ForEach-Object { $_ }

if (-not (Test-Path (Join-Path $tmp '.git'))) { Write-Host 'CLONE FAILED'; exit 1 }
Write-Host ("cloned to " + $tmp)

Write-Host '=== 2) copy project files (excluding heavy/derived dirs) ==='
$exDirs = @('work', 'nx', 'build', 'keystore', 'backup-modules-20261113', 'app', 'shots', 'obj', 'bin', '.git', 'javac-check', 'node_modules', '__pycache__')
$exFiles = @('*.apk', '*.dex', '*.odex', '*.vdex', '*.oat', '*.zip', '*.tar', '*.tar.gz', '*.jar', '*.class', '*.log', '*.db', '*.db-shm', '*.db-wal', '*.keystore', '*.jks', '*.p12', '*.pem', '*.swp', '*.bak', '*.orig', '*.rej', 'Thumbs.db', 'Desktop.ini')
$robArgs = @($src, $tmp, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:0', '/W:0', '/XD') + $exDirs + @('/XF') + $exFiles
& robocopy @robArgs | Out-Null
Write-Host ("robocopy exit = " + $LASTEXITCODE + " (0-7 = ok)")

Write-Host '=== 3) git add ==='
Git @('-C', $tmp, 'add', '-A') | ForEach-Object { $_ }
$status = Git @('-C', $tmp, 'status', '--porcelain')
$changed = @($status | Where-Object { $_.Trim().Length -gt 0 })
Write-Host ("changed entries: " + $changed.Count)
$changed | Select-Object -First 25 | ForEach-Object { Write-Host ("   " + $_) }

Write-Host '=== 4) sanity: no binaries staged ==='
$binaries = @($status | Where-Object { $_ -match '\.(apk|dex|odex|vdex|oat|zip|tar|jar|class|keystore|jks|p12|pem|db|log)$' })
if ($binaries.Count -gt 0) {
    Write-Host 'BINARIES DETECTED - abort'
    $binaries | Select-Object -First 10 | ForEach-Object { Write-Host ("   " + $_) }
    exit 2
}
Write-Host 'no binaries staged'

Write-Host '=== 5) commit ==='
Git @('-C', $tmp, 'config', 'user.name', 'hszlk') | Out-Null
Git @('-C', $tmp, 'config', 'user.email', 'hszlk@users.noreply.github.com') | Out-Null
Git @('-C', $tmp, 'commit', '-m', 'docs: add README, .gitignore and clean-room SystemUI sources', '--quiet') | ForEach-Object { $_ }
Git @('-C', $tmp, 'log', '--oneline', '-3') | ForEach-Object { Write-Host ("   " + $_) }

Write-Host '=== 6) push ==='
Git @('-C', $tmp, 'push', 'origin', 'HEAD:main') | ForEach-Object { Write-Host ("   " + $_) }
Write-Host '=== done ==='

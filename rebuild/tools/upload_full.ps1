$ErrorActionPreference = 'Continue'
$git  = 'D:\PortableGit\cmd\git.exe'
$src  = 'D:\DEBUG\apk\sysui'
$tmp  = Join-Path $env:TEMP 'aio_repo_full'
$repo = 'https://github.com/hszlk/AIO_XTC_systemui.git'

Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices;
public class CR3 {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public struct C { public uint Flags; public uint Type; public string TargetName; public string Comment;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten; public uint Size; public IntPtr Blob;
    public uint Persist; public uint AttrCount; public IntPtr Attrs; public string Alias; public string UserName; }
  [DllImport("advapi32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool CredRead(string t, uint ty, uint f, out IntPtr c);
  [DllImport("advapi32.dll")] public static extern void CredFree(IntPtr c);
  public static string Get(string t){ IntPtr p; if(!CredRead(t,1,0,out p)) return null;
    try{ C c=(C)Marshal.PtrToStructure(p,typeof(C)); if(c.Size==0) return null;
      byte[] b=new byte[c.Size]; Marshal.Copy(c.Blob,b,0,b.Length);
      string s=System.Text.Encoding.Unicode.GetString(b); int z=s.IndexOf('\0'); return z>=0?s.Substring(0,z):s.TrimEnd('\0');
    } finally { CredFree(p); } } }
"@
$tok = [CR3]::Get('git:https://hszlk@github.com')
if (-not $tok) { Write-Host 'NO TOKEN'; exit 1 }
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("hszlk:$tok"))
$auth = "http.https://github.com/.extraheader=AUTHORIZATION: basic $b64"
$env:GIT_TERMINAL_PROMPT = '0'
function G([string[]]$argv) {
    $o = & $git -c $auth @argv 2>&1
    $o | ForEach-Object { Write-Host ("   " + ($_ -replace '(https://)[^@\s]*@','$1***@')) }
    return $LASTEXITCODE
}

Write-Host '=== 1) 准备发布产物 dist/ ==='
$dist = Join-Path $src 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$apk = Join-Path $src 'rebuild\build\XTCSystemUI-hardened.apk'
$mod = Join-Path $src 'rebuild\build\XTCSystemUI-v82-sysui-shell.zip'
if (Test-Path $apk) { Copy-Item $apk (Join-Path $dist 'XTCSystemUI-hardened.apk') -Force; Write-Host ("   apk -> " + (Get-Item (Join-Path $dist 'XTCSystemUI-hardened.apk')).Length) }
if (Test-Path $mod) { Copy-Item $mod (Join-Path $dist 'XTCSystemUI-v82-dual-module.zip') -Force; Write-Host ("   module -> " + (Get-Item (Join-Path $dist 'XTCSystemUI-v82-dual-module.zip')).Length) }

Write-Host '=== 2) .gitignore 增加 dist/ 例外 ==='
$gi = Join-Path $src '.gitignore'
$marker = '# ---- 发布产物（明确纳入版本管理）----'
$giText = Get-Content $gi -Raw -Encoding UTF8
if ($giText -notmatch [regex]::Escape($marker)) {
    $add = "`r`n$marker`r`n!/dist/`r`n!/dist/*.apk`r`n!/dist/*.zip`r`n"
    [System.IO.File]::AppendAllText($gi, $add, [System.Text.UTF8Encoding]::new($false))
    Write-Host '   appended'
}

Write-Host '=== 3) clone ==='
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
$code = G @('clone', '--quiet', $repo, $tmp)
if (-not (Test-Path (Join-Path $tmp '.git'))) { Write-Host ("CLONE FAILED exit=$code"); exit 1 }
Write-Host '   cloned'

Write-Host '=== 4) 复制整个构建工程（仅排除超大派生目录 / 签名私钥）==='
$exDirs = @(
    (Join-Path $src 'work'),
    (Join-Path $src 'nx'),
    (Join-Path $src 'rebuild\keystore'),
    (Join-Path $src 'rebuild\backup-modules-20261113'),
    (Join-Path $src 'analysis\shots'),
    (Join-Path $src 'analysis\cloud-control\build'),
    (Join-Path $src 'rebuild\build\obj'),
    (Join-Path $tmp '.git')
)
# 不排除 rebuild\build 本身，但排除中间件，只保留可分发产物（由 dist 承载）
$exDirs += (Join-Path $src 'rebuild\build\obj')
$exFiles = @('*.dex','*.odex','*.vdex','*.oat','*.class','*.log','*.db','*.db-shm','*.db-wal','*.keystore','*.jks','*.p12','*.pem','*.swp','*.bak','*.orig','*.rej','Thumbs.db','Desktop.ini','_dump_resources.txt','_scan_report.txt','_badging.txt')
$robArgs = @($src, $tmp, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:0', '/W:0', '/XD') + $exDirs + @('/XF') + $exFiles
& robocopy @robArgs | Out-Null
Write-Host ("   robocopy exit = " + $LASTEXITCODE)

Write-Host '=== 5) add + 检查 ==='
G @('-C', $tmp, 'add', '-A') | Out-Null
$st = & $git -C $tmp status --porcelain 2>&1
$changed = @($st | Where-Object { $_.Trim() })
Write-Host ("   changed entries: " + $changed.Count)
$changed | Select-Object -First 15 | ForEach-Object { Write-Host ("   " + $_) }
Write-Host '   ... 大文件检查（>2MB 的新增项）:'
$big = @($changed | ForEach-Object {
    $p = ($_ -replace '^\s*\S+\s+','').Trim()
    $f = Join-Path $tmp ($p -replace '/','\')
    if (Test-Path $f) { $i = Get-Item $f; if ($i.Length -gt 2MB) { "{0}  {1:N1}MB" -f $p, ($i.Length/1MB) } }
})
if ($big) { $big | ForEach-Object { Write-Host ("      " + $_) } } else { Write-Host '      (none)' }

Write-Host '=== 6) commit + push ==='
G @('-C', $tmp, 'config', 'user.name', 'hszlk') | Out-Null
G @('-C', $tmp, 'config', 'user.email', 'hszlk@users.noreply.github.com') | Out-Null
$null = G @('-C', $tmp, 'commit', '-m', 'build: upload full build project (tools, analysis, sources, dist artifacts)', '--quiet')
G @('-C', $tmp, 'push', 'origin', 'HEAD:main')
G @('-C', $tmp, 'log', '--oneline', '-3')

$tracked = @(& $git -C $tmp ls-files)
Write-Host ("   tracked files now: " + $tracked.Count)
Write-Host ("   java files: " + @($tracked | Where-Object { $_ -like 'src/*.java' }).Count)
Write-Host ("   dist entries: " + (@($tracked | Where-Object { $_ -like 'dist/*' }) -join ', '))

$ErrorActionPreference = 'Continue'
$git = 'D:\PortableGit\cmd\git.exe'
$src = 'D:\DEBUG\apk\sysui'
$tmp = Join-Path $env:TEMP 'aio_xtc_repo2'
$repo = 'https://github.com/hszlk/AIO_XTC_systemui.git'

# --- 从 Windows 凭据管理器取回 token（用完立即删除）---
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CredR {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public uint Flags; public uint Type; public string TargetName; public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize; public IntPtr CredentialBlob; public uint Persist;
        public uint AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName;
    }
    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CredRead(string target, uint type, uint flags, out IntPtr credential);
    [DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
    public static string Get(string target) {
        IntPtr p;
        if (!CredRead(target, 1, 0, out p)) return null;
        try {
            CREDENTIAL c = (CREDENTIAL)Marshal.PtrToStructure(p, typeof(CREDENTIAL));
            if (c.CredentialBlobSize == 0) return null;
            byte[] b = new byte[c.CredentialBlobSize];
            Marshal.Copy(c.CredentialBlob, b, 0, b.Length);
            string s = System.Text.Encoding.Unicode.GetString(b);
            int z = s.IndexOf('\0');
            return z >= 0 ? s.Substring(0, z) : s.TrimEnd('\0');
        } finally { CredFree(p); }
    }
}
"@
$tok = [CredR]::Get('git:https://hszlk@github.com')
if (-not $tok) { Write-Host 'NO TOKEN'; exit 1 }
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("hszlk:$tok"))
$authOpt = "http.https://github.com/.extraheader=AUTHORIZATION: basic $b64"
$env:GIT_TERMINAL_PROMPT = '0'
function G([string[]]$argv) { & $git -c $authOpt @argv 2>&1 }

Write-Host '=== clone ==='
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
G @('clone', '--quiet', $repo, $tmp) | Out-Null
if (-not (Test-Path (Join-Path $tmp '.git'))) { Write-Host 'CLONE FAILED'; exit 1 }

Write-Host '=== copy with SAFE exclusions (full paths only) ==='
$exDirs = @(
    (Join-Path $src 'work'),
    (Join-Path $src 'nx'),
    (Join-Path $src 'rebuild\build'),
    (Join-Path $src 'rebuild\keystore'),
    (Join-Path $src 'rebuild\backup-modules-20261113'),
    (Join-Path $src 'rebuild\app'),
    (Join-Path $src 'analysis\shots'),
    (Join-Path $src 'analysis\cloud-control\build'),
    (Join-Path $tmp '.git')
)
$exFiles = @('*.apk','*.dex','*.odex','*.vdex','*.oat','*.zip','*.tar','*.tar.gz','*.jar','*.class','*.log','*.db','*.db-shm','*.db-wal','*.keystore','*.jks','*.p12','*.pem','*.swp','*.bak','*.orig','*.rej','Thumbs.db','Desktop.ini','_dump_resources.txt','_scan_report.txt','_badging.txt')
$args = @($src, $tmp, '/E', '/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/R:0', '/W:0', '/XD') + $exDirs + @('/XF') + $exFiles
& robocopy @args | Out-Null
Write-Host ("robocopy exit = " + $LASTEXITCODE)

G @('-C', $tmp, 'add', '-A') | Out-Null
$st = G @('-C', $tmp, 'status', '--porcelain')
$changed = @($st | Where-Object { $_.Trim() })
Write-Host ("changed entries: " + $changed.Count)
$changed | Select-Object -First 20 | ForEach-Object { Write-Host ("   " + $_) }

$bad = @($changed | Where-Object { $_ -match '\.(apk|dex|odex|vdex|oat|zip|tar|tar\.gz|jar|class|keystore|jks|p12|pem|db|log)$' })
if ($bad.Count -gt 0) { Write-Host 'ABORT: binary staged'; $bad | Select-Object -First 10 | ForEach-Object { Write-Host ("   " + $_) }; exit 2 }

if ($changed.Count -gt 0) {
    G @('-C', $tmp, 'config', 'user.name', 'hszlk') | Out-Null
    G @('-C', $tmp, 'config', 'user.email', 'hszlk@users.noreply.github.com') | Out-Null
    G @('-C', $tmp, 'commit', '-m', 'fix: add missing androidx/core/app/CoreComponentFactory.java (full source set)', '--quiet') | Out-Null
    Write-Host '=== push ==='
    G @('-C', $tmp, 'push', 'origin', 'HEAD:main') | ForEach-Object { Write-Host ("   " + ($_ -replace '(https://)[^@]*@','$1***@')) }
}
G @('-C', $tmp, 'log', '--oneline', '-3') | ForEach-Object { Write-Host ("   " + $_) }

$javaCount = @(& $git -C $tmp ls-files 'src/*.java').Count
Write-Host ("java files now in repo: " + $javaCount)

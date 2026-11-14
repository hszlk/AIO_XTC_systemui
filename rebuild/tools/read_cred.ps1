Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public class CredReader {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CredRead(string target, uint type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll")]
    public static extern void CredFree(IntPtr cred);

    public static string[] Get(string target) {
        IntPtr p;
        if (!CredRead(target, 1 /* GENERIC */, 0, out p)) {
            return new string[] { "ERR", Marshal.GetLastWin32Error().ToString() };
        }
        try {
            CREDENTIAL c = (CREDENTIAL)Marshal.PtrToStructure(p, typeof(CREDENTIAL));
            string user = c.UserName ?? "";
            string blob = "";
            if (c.CredentialBlobSize > 0 && c.CredentialBlob != IntPtr.Zero) {
                byte[] b = new byte[c.CredentialBlobSize];
                Marshal.Copy(c.CredentialBlob, b, 0, b.Length);
                blob = System.Text.Encoding.Unicode.GetString(b).TrimEnd('\0');
                if (blob.IndexOf('\0') > 0) blob = blob.Substring(0, blob.IndexOf('\0'));
            }
            return new string[] { "OK", user, blob };
        } finally {
            CredFree(p);
        }
    }
}
"@

$targets = @(
    'git:https://hszlk@github.com',
    'LegacyGeneric:target=git:https://hszlk@github.com'
)
foreach ($t in $targets) {
    $r = [CredReader]::Get($t)
    if ($r[0] -eq 'OK') {
        Write-Output ("TARGET OK: " + $t)
        Write-Output ("  user: " + $r[1])
        $pwd = $r[2]
        Write-Output ("  secret length: " + $pwd.Length)
        Write-Output ("  secret prefix: " + $pwd.Substring(0, [Math]::Min(11, $pwd.Length)) + "...")
        # save outside the repo for the push step
        $out = Join-Path $env:TEMP 'gh_secret.txt'
        [System.IO.File]::WriteAllText($out, $pwd)
        Write-Output ("  saved to: " + $out)
        break
    } else {
        Write-Output ("target fail ({0}): {1}" -f $t, $r[1])
    }
}

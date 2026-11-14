$ErrorActionPreference = 'Continue'

function Ensure-Device([int]$tries = 25) {
    for ($i = 1; $i -le $tries; $i++) {
        $st = ''
        try { $st = (& adb get-state 2>$null | Out-String).Trim() } catch { $st = '' }
        if ($st -eq 'device') { return $true }
        try { & adb reconnect offline 2>&1 | Out-Null } catch {}
        Start-Sleep -Seconds 3
    }
    return $false
}

function Sh([string]$cmd, [int]$tries = 6) {
    for ($i = 1; $i -le $tries; $i++) {
        if (-not (Ensure-Device 12)) { continue }
        $out = ''
        try { $out = (& adb shell $cmd 2>&1 | Out-String) } catch { $out = '' }
        if ($out.Trim().Length -gt 0) { return $out.Trim() }
        Start-Sleep -Seconds 1
    }
    return ''
}

if (-not (Ensure-Device)) { Write-Host 'NO DEVICE'; exit 1 }

Write-Host '--- list -u (include uninstalled) ---'
Write-Host (Sh 'pm list packages -u | grep systemui')
Write-Host '--- packages.xml entry ---'
Write-Host (Sh 'su -c "grep -c \"com.android.systemui\" /data/system/packages.xml"')
Write-Host '--- restrictions ---'
Write-Host (Sh 'su -c "grep -o \"com.android.systemui\" /data/system/users/0/package-restrictions.xml | head -3"')
Write-Host '--- system apk present? ---'
Write-Host (Sh 'ls -l /system/priv-app/XTCSystemUI/')
Write-Host '--- dalvik cache ---'
Write-Host (Sh 'ls -l /data/dalvik-cache/arm/ 2>/dev/null | grep -i systemui | head -5')

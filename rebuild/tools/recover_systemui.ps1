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

Write-Host '=== reinstall the system package from its /system path ==='
Write-Host (Sh 'pm install -r -d /system/priv-app/XTCSystemUI/XTCSystemUI.apk')
Start-Sleep -Seconds 4
Write-Host ('pm path: ' + (Sh 'pm path com.android.systemui'))
Write-Host ('listed: ' + (Sh 'pm list packages | grep com.android.systemui'))

Write-Host '=== start SystemUI service ==='
Write-Host (Sh 'am startservice -n com.android.systemui/.SystemUIService')
Start-Sleep -Seconds 8
Write-Host ('pid: ' + (Sh 'pidof com.android.systemui'))

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

Write-Host ("pid = " + (Sh 'pidof com.android.systemui'))
Write-Host ("live apk = " + (Sh 'md5sum /system/priv-app/XTCSystemUI/XTCSystemUI.apk'))

Write-Host '--- home + expand panel ---'
Sh 'input keyevent KEYCODE_WAKEUP' | Out-Null
Sh 'input keyevent KEYCODE_HOME' | Out-Null
Start-Sleep -Seconds 2
Sh 'input swipe 160 12 160 280 260' | Out-Null
Start-Sleep -Seconds 2

Write-Host '--- dump ---'
$dump = Sh 'uiautomator dump /sdcard/ui3.xml'
Write-Host $dump

$local = 'D:\DEBUG\apk\sysui\work\ui_current3.xml'
Remove-Item $local -ErrorAction SilentlyContinue
for ($i = 1; $i -le 8; $i++) {
    if (-not (Ensure-Device 12)) { continue }
    $r = (& adb pull /sdcard/ui3.xml $local 2>&1 | Out-String)
    if (Test-Path $local) { break }
    Start-Sleep -Seconds 1
}
if (Test-Path $local) {
    Write-Host "dumped $((Get-Item $local).Length) bytes"
} else {
    Write-Host 'pull failed'
}

Write-Host '--- SystemUI log tail ---'
Write-Host (Sh 'logcat -d -v brief -s QSPanel:* XtcStatusBarView:* AndroidRuntime:E 2>/dev/null | tail -20')

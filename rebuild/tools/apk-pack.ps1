#requires -Version 5.1
<#
.SYNOPSIS
    Build the Mode A APK skeleton: copy every entry of the original APK verbatim,
    drop the stale v1 signature files, append a fresh classes.dex.

.DESCRIPTION
    Mode A keeps the original APK's resources bit-for-bit identical
    (resources.arsc, res/**, assets/**, lib/**, AndroidManifest.xml, root files),
    so the delivered APK has exactly the same resource table, component list,
    locales and permissions as the vendor APK. Only the code changes.

    This script does raw ZIP surgery instead of using a zip library:
      * every kept entry is copied as its original local-header + compressed
        payload bytes, so the compression method (STORE vs DEFLATE), CRCs and
        payloads are preserved exactly;
      * the central directory is rebuilt with corrected local-header offsets;
      * classes.dex is appended as one new DEFLATE entry.

    .NET's ZipArchive "Update" mode was tried and rejected: it rewrote the
    archive with inconsistent local-header sizes (aapt2 reported
    "size/crc32 mismatch" for AndroidManifest.xml), which is exactly the kind of
    silent resource corruption Mode A must never risk.

    Output still needs zipalign + apksigner (see tools\build.ps1).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourceApk,
    [Parameter(Mandatory = $true)][string]$DexFile,
    [Parameter(Mandatory = $true)][string]$OutputApk,
    [string[]]$DropEntries = @('META-INF/MANIFEST.MF', 'META-INF/CERT.SF', 'META-INF/CERT.RSA')
)

$ErrorActionPreference = 'Stop'

# ----------------------------------------------------------------- CRC32
# PowerShell 5.1 parses 0xEDB88320 / 0xFFFFFFFF as *negative Int32*. Sign
# extending those to Int64 and then using -shr gives an ARITHMETIC shift, which
# silently corrupts the whole CRC (the first shipped build had a classes.dex
# entry whose CRC-32 failed zipfile/`adb` verification). Everything below uses
# decimal 4294967295 and the self-test below guards the vector.
$script:CrcTable = New-Object 'int64[]' 256
for ($n = 0; $n -lt 256; $n++) {
    $c = [int64]$n
    for ($k = 0; $k -lt 8; $k++) {
        if ($c -band 1) { $c = (3988292384 -bxor ($c -shr 1)) -band 4294967295 }
        else { $c = $c -shr 1 }
    }
    $script:CrcTable[$n] = $c
}

function Get-Crc32([byte[]]$Data) {
    $crc = [int64]4294967295
    foreach ($b in $Data) {
        $idx = [int](($crc -bxor [int64]$b) -band 0xFF)
        $crc = ($script:CrcTable[$idx] -bxor ($crc -shr 8)) -band 4294967295
    }
    return [uint32](($crc -bxor 4294967295) -band 4294967295)
}

# Canonical CRC-32 check vector ("123456789" -> 0xCBF43926). Fail loudly rather
# than write an APK with a bad entry CRC.
$script:CrcSelfTest = Get-Crc32 ([System.Text.Encoding]::ASCII.GetBytes('123456789'))
if ($script:CrcSelfTest -ne [uint32]3421780262) {
    throw ('CRC32 self-test failed: got 0x{0:x8}, expected 0xcbf43926' -f $script:CrcSelfTest)
}

function Get-U16([byte[]]$Buf, [int]$Off) { return [BitConverter]::ToUInt16($Buf, $Off) }
function Get-U32([byte[]]$Buf, [int]$Off) { return [BitConverter]::ToUInt32($Buf, $Off) }
function Set-U32([byte[]]$Buf, [int]$Off, [uint32]$Value) {
    $bytes = [BitConverter]::GetBytes([uint32]$Value)
    [Array]::Copy($bytes, 0, $Buf, $Off, 4)
}
function Set-U16([byte[]]$Buf, [int]$Off, [uint16]$Value) {
    $bytes = [BitConverter]::GetBytes([uint16]$Value)
    [Array]::Copy($bytes, 0, $Buf, $Off, 2)
}

# ------------------------------------------------------- read source APK
if (-not (Test-Path $SourceApk)) { throw "source APK not found: $SourceApk" }
$src = [System.IO.File]::OpenRead($SourceApk)
try {
    $fileLen = $src.Length
    if ($fileLen -lt 22) { throw "source APK too small to be a zip" }

    # End of central directory: scan the last 64 KiB for the signature.
    $tailLen = [int][Math]::Min(66000, $fileLen)
    $tail = New-Object byte[] $tailLen
    $src.Position = $fileLen - $tailLen
    $read = 0
    while ($read -lt $tailLen) {
        $n = $src.Read($tail, $read, $tailLen - $read)
        if ($n -le 0) { break }
        $read += $n
    }
    $eocd = -1
    for ($i = $tailLen - 22; $i -ge 0; $i--) {
        if ($tail[$i] -eq 0x50 -and $tail[$i + 1] -eq 0x4b -and $tail[$i + 2] -eq 0x05 -and $tail[$i + 3] -eq 0x06) {
            $eocd = $i; break
        }
    }
    if ($eocd -lt 0) { throw 'no end-of-central-directory record found (not a zip)' }

    $diskEntries = Get-U16 $tail ($eocd + 8)
    $totalEntries = Get-U16 $tail ($eocd + 10)
    $cdSize = Get-U32 $tail ($eocd + 12)
    $cdOffset = Get-U32 $tail ($eocd + 16)
    if ($diskEntries -eq 0xFFFF -or $totalEntries -eq 0xFFFF -or $cdOffset -eq 0xFFFFFFFF) {
        throw 'zip64 archives are not supported by this packer'
    }

    $cd = New-Object byte[] $cdSize
    $src.Position = $cdOffset
    $read = 0
    while ($read -lt $cdSize) {
        $n = $src.Read($cd, $read, $cdSize - $read)
        if ($n -le 0) { break }
        $read += $n
    }

    # ------------------------------------------------- parse central directory
    $entries = New-Object System.Collections.Generic.List[object]
    $p = 0
    for ($i = 0; $i -lt $totalEntries; $i++) {
        if ((Get-U32 $cd $p) -ne [uint32]0x02014b50) { throw "bad central directory signature at offset $p" }
        $flags = Get-U16 $cd ($p + 8)
        $method = Get-U16 $cd ($p + 10)
        $crc = Get-U32 $cd ($p + 16)
        $compSize = Get-U32 $cd ($p + 20)
        $uncompSize = Get-U32 $cd ($p + 24)
        $nameLen = Get-U16 $cd ($p + 28)
        $extraLen = Get-U16 $cd ($p + 30)
        $commentLen = Get-U16 $cd ($p + 32)
        $localOffset = Get-U32 $cd ($p + 42)
        $name = [System.Text.Encoding]::UTF8.GetString($cd, $p + 46, $nameLen)
        $entries.Add([pscustomobject]@{
                Name        = $name
                Flags       = $flags
                Method      = $method
                Crc         = $crc
                CompSize    = $compSize
                UncompSize  = $uncompSize
                LocalOffset = $localOffset
                CdHeader    = [byte[]]$cd[($p)..($p + 45)]
                CdExtra     = $(if ($extraLen -gt 0) { [byte[]]$cd[($p + 46 + $nameLen)..($p + 45 + $nameLen + $extraLen)] } else { New-Object byte[] 0 })
            })
        $p += 46 + $nameLen + $extraLen + $commentLen
    }

    # ------------------------------------------------------- write new APK
    $outDir = Split-Path -Parent $OutputApk
    if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    if (Test-Path $OutputApk) { Remove-Item $OutputApk -Force }

    $out = [System.IO.File]::Create($OutputApk)
    try {
        $newCd = New-Object System.IO.MemoryStream
        $kept = 0
        $dropped = New-Object System.Collections.Generic.List[string]
        $buf = New-Object byte[] 1048576

        function Copy-Bytes($From, $To, [long]$Count) {
            $left = [long]$Count
            while ($left -gt 0) {
                $want = [int][Math]::Min([long]$buf.Length, $left)
                $got = $From.Read($buf, 0, $want)
                if ($got -le 0) { throw 'unexpected end of source archive' }
                $To.Write($buf, 0, $got)
                $left -= $got
            }
        }

        foreach ($e in $entries) {
            if ($DropEntries -contains $e.Name) { $dropped.Add($e.Name); continue }

            $newOffset = [uint32]$out.Position
            $src.Position = [long]$e.LocalOffset
            # local header: 30 fixed bytes + name + extra
            $lh = New-Object byte[] 30
            $src.Read($lh, 0, 30) | Out-Null
            if ((Get-U32 $lh 0) -ne [uint32]0x04034b50) { throw "bad local header signature for $($e.Name)" }
            $lNameLen = Get-U16 $lh 26
            $lExtraLen = Get-U16 $lh 28

            # The vendor APK is a *streaming* zip: 842 of its 1131 entries set the
            # data-descriptor flag (bit 3), so the local header carries zero
            # crc/sizes and the real values follow the payload. aapt2 refuses to
            # open such an archive after the entries are relocated
            # ("size/crc32 mismatch ... failed opening zip"). Canonicalise every
            # entry instead: write the authoritative central-directory crc/sizes
            # into the local header, clear bit 3 and drop the trailing descriptor.
            # The compressed payload itself is still copied byte-for-byte.
            $lFlags = Get-U16 $lh 6
            $hadDescriptor = ($lFlags -band 0x0008) -ne 0
            if ($hadDescriptor) {
                Set-U16 $lh 6 ([uint16]($lFlags -band 0xFFF7))
                Set-U32 $lh 14 $e.Crc
                Set-U32 $lh 18 $e.CompSize
                Set-U32 $lh 22 $e.UncompSize
            }

            $out.Write($lh, 0, 30)
            Copy-Bytes $src $out ($lNameLen + $lExtraLen)
            Copy-Bytes $src $out ([long]$e.CompSize)

            # central directory record, verbatim except flags + local-header offset
            $rec = [byte[]]$e.CdHeader.Clone()
            if ($hadDescriptor) {
                $cdFlags = Get-U16 $rec 8
                Set-U16 $rec 8 ([uint16]($cdFlags -band 0xFFF7))
            }
            Set-U32 $rec 42 $newOffset
            $newCd.Write($rec, 0, 46)
            $nameBytes = [System.Text.Encoding]::UTF8.GetBytes($e.Name)
            $newCd.Write($nameBytes, 0, $nameBytes.Length)
            if ($e.CdExtra.Length -gt 0) { $newCd.Write($e.CdExtra, 0, $e.CdExtra.Length) }
            $kept++
        }

        # --------------------------------------------------- append classes.dex
        if (-not (Test-Path $DexFile)) { throw "dex not found: $DexFile" }
        $dexBytes = [System.IO.File]::ReadAllBytes($DexFile)
        $dexCrc = Get-Crc32 $dexBytes
        $deflated = New-Object System.IO.MemoryStream
        $ds = New-Object System.IO.Compression.DeflateStream($deflated, [System.IO.Compression.CompressionMode]::Compress, $true)
        $ds.Write($dexBytes, 0, $dexBytes.Length)
        $ds.Dispose()
        $dexComp = $deflated.ToArray()
        $dexName = [System.Text.Encoding]::ASCII.GetBytes('classes.dex')

        $dexOffset = [uint32]$out.Position
        $lhDex = New-Object byte[] 30
        Set-U32 $lhDex 0 ([uint32]0x04034b50)
        Set-U16 $lhDex 4 ([uint16]20)          # version needed: 2.0
        Set-U16 $lhDex 6 ([uint16]0)           # flags
        Set-U16 $lhDex 8 ([uint16]8)           # method: deflate
        Set-U16 $lhDex 10 ([uint16]0)          # mod time
        Set-U16 $lhDex 12 ([uint16]0x21)       # mod date (1980-01-01)
        Set-U32 $lhDex 14 $dexCrc
        Set-U32 $lhDex 18 ([uint32]$dexComp.Length)
        Set-U32 $lhDex 22 ([uint32]$dexBytes.Length)
        Set-U16 $lhDex 26 ([uint16]$dexName.Length)
        Set-U16 $lhDex 28 ([uint16]0)
        $out.Write($lhDex, 0, 30)
        $out.Write($dexName, 0, $dexName.Length)
        $out.Write($dexComp, 0, $dexComp.Length)

        $cdDex = New-Object byte[] 46
        Set-U32 $cdDex 0 ([uint32]0x02014b50)
        Set-U16 $cdDex 4 ([uint16]20)          # version made by
        Set-U16 $cdDex 6 ([uint16]20)          # version needed
        Set-U16 $cdDex 8 ([uint16]0)           # flags
        Set-U16 $cdDex 10 ([uint16]8)          # method
        Set-U16 $cdDex 12 ([uint16]0)
        Set-U16 $cdDex 14 ([uint16]0x21)
        Set-U32 $cdDex 16 $dexCrc
        Set-U32 $cdDex 20 ([uint32]$dexComp.Length)
        Set-U32 $cdDex 24 ([uint32]$dexBytes.Length)
        # central directory record layout: 16 crc, 20 comp, 24 uncomp,
        # 28 nameLen, 30 extraLen, 32 commentLen, 34 diskStart,
        # 36 internalAttrs, 38 externalAttrs, 42 localHeaderOffset
        Set-U16 $cdDex 28 ([uint16]$dexName.Length)
        Set-U16 $cdDex 30 ([uint16]0)
        Set-U16 $cdDex 32 ([uint16]0)
        Set-U16 $cdDex 34 ([uint16]0)
        Set-U16 $cdDex 36 ([uint16]0)
        Set-U32 $cdDex 38 ([uint32]0)          # external attributes
        Set-U32 $cdDex 42 $dexOffset
        $newCd.Write($cdDex, 0, 46)
        $newCd.Write($dexName, 0, $dexName.Length)
        $kept++

        # --------------------------------------------------- EOCD
        $cdStart = [uint32]$out.Position
        $cdBytes = $newCd.ToArray()
        $out.Write($cdBytes, 0, $cdBytes.Length)
        $eocdOut = New-Object byte[] 22
        Set-U32 $eocdOut 0 ([uint32]0x06054b50)
        Set-U16 $eocdOut 4 ([uint16]0)
        Set-U16 $eocdOut 6 ([uint16]0)
        Set-U16 $eocdOut 8 ([uint16]$kept)
        Set-U16 $eocdOut 10 ([uint16]$kept)
        Set-U32 $eocdOut 12 ([uint32]$cdBytes.Length)
        Set-U32 $eocdOut 16 $cdStart
        Set-U16 $eocdOut 20 ([uint16]0)
        $out.Write($eocdOut, 0, 22)
    }
    finally {
        $out.Dispose()
    }
}
finally {
    $src.Dispose()
}

Write-Host ("   packed {0}: {1} entries kept, {2} dropped ({3}), classes.dex {4:N0} B -> {5:N0} B deflated" -f `
        (Split-Path -Leaf $OutputApk), $kept, $dropped.Count, ($dropped -join ', '), (Get-Item $DexFile).Length, (Get-Item $OutputApk).Length)

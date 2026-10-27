#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Interactive Phone Debloater & Optimizer (Samsung, Xiaomi/POCO, OnePlus/Realme/Oppo, Vivo, Motorola, Pixel).
#>

$ErrorActionPreference = "Stop"

# Locate adb
$ADB_BIN = "adb"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    $sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) {
        $ADB_BIN = $sdkAdb
    } else {
        Write-Error "ADB executable not found. Please add ADB to PATH or install Android platform-tools."
        exit 1
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   📱 Android Phone Debloater Toolkit    " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Device Detection
$rawDevices = & $ADB_BIN devices
$connected = @()
foreach ($line in ($rawDevices -split "`r?`n")) {
    if ($line -match '^([^\s]+)\s+device$') {
        $connected += $matches[1]
    }
}

if ($connected.Count -eq 0) {
    Write-Warning "No connected ADB devices detected. Ensure USB Debugging or Wireless ADB is enabled."
    exit 1
}

$TARGET_DEVICE = $connected[0]
if ($connected.Count -gt 1) {
    Write-Host "Multiple devices detected. Select target:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $connected.Count; $i++) {
        Write-Host "  [$i] $($connected[$i])"
    }
    $idx = Read-Host "Enter device index [0]"
    if ($idx -match '^\d+$' -and [int]$idx -lt $connected.Count) {
        $TARGET_DEVICE = $connected[[int]$idx]
    }
}

Write-Host "🎯 Target Device: $TARGET_DEVICE" -ForegroundColor Green

# Device Metadata
$brand = (& $ADB_BIN -s $TARGET_DEVICE shell getprop ro.product.brand).Trim()
$model = (& $ADB_BIN -s $TARGET_DEVICE shell getprop ro.product.model).Trim()
$androidVer = (& $ADB_BIN -s $TARGET_DEVICE shell getprop ro.build.version.release).Trim()

Write-Host "Brand: $brand | Model: $model | Android: $androidVer" -ForegroundColor Gray

# 2. Backup Current State
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$backupDir = Join-Path $scriptDir "backups\$($model -replace '\s+','_')_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

Write-Host "`n📦 Creating safety baseline snapshot in: $backupDir" -ForegroundColor Yellow
& $ADB_BIN -s $TARGET_DEVICE shell "pm list packages -e" > (Join-Path $backupDir "enabled_packages.txt")
& $ADB_BIN -s $TARGET_DEVICE shell "pm list packages -d" > (Join-Path $backupDir "disabled_packages.txt")
& $ADB_BIN -s $TARGET_DEVICE shell "pm list packages -u" > (Join-Path $backupDir "uninstalled_packages.txt")
Write-Host "✔ Backup completed." -ForegroundColor Green

# 3. Load Protected & Bloat Database
$protectedFile = Join-Path $scriptDir "protected.txt"
$bloatFile = Join-Path $scriptDir "bloat_database.txt"

$protectedPkgs = @()
if (Test-Path $protectedFile) {
    $protectedPkgs = Get-Content $protectedFile | Where-Object { $_ -match '^[a-zA-Z0-9_\.]+$' }
}

$bloatCandidates = @()
if (Test-Path $bloatFile) {
    $bloatCandidates = Get-Content $bloatFile | Where-Object { $_ -match '^[a-zA-Z0-9_\.]+$' }
}

# 4. Scan Installed Packages on Target
$installedRaw = & $ADB_BIN -s $TARGET_DEVICE shell "pm list packages"
$installed = @()
foreach ($line in ($installedRaw -split "`r?`n")) {
    if ($line -match '^package:(.+)$') {
        $installed += $matches[1].Trim()
    }
}

# Match detected bloat
$detectedBloat = @()
foreach ($pkg in $bloatCandidates) {
    if ($installed -contains $pkg -and ($protectedPkgs -notcontains $pkg)) {
        $detectedBloat += $pkg
    }
}

Write-Host "`n🔍 Scan Results:" -ForegroundColor Cyan
Write-Host "Total Installed Packages: $($installed.Count)"
Write-Host "Known Bloatware Detected: $($detectedBloat.Count)"

if ($detectedBloat.Count -eq 0) {
    Write-Host "No common bloatware packages from database found on this device!" -ForegroundColor Green
} else {
    Write-Host "`nIdentified Candidates:" -ForegroundColor Yellow
    foreach ($b in $detectedBloat) {
        Write-Host "  - $b"
    }
}

# 5. Menu Actions
Write-Host "`nActions:" -ForegroundColor Cyan
Write-Host "1. Debloat detected candidates (Safe uninstall for current user: pm uninstall -k --user 0)"
Write-Host "2. Disable detected candidates (Reversible: pm disable-user --user 0)"
Write-Host "3. Manual package debloat / uninstall"
Write-Host "4. Restore all from backup snapshot"
Write-Host "5. Exit"

$choice = Read-Host "`nEnter selection (1-5)"

switch ($choice) {
    "1" {
        $confirm = Read-Host "Proceed with user-0 uninstall of $($detectedBloat.Count) packages? (y/N)"
        if ($confirm -eq "y" -or $confirm -eq "Y") {
            foreach ($pkg in $detectedBloat) {
                Write-Host "Removing: $pkg ..." -NoNewline
                $res = & $ADB_BIN -s $TARGET_DEVICE shell "pm uninstall -k --user 0 $pkg"
                Write-Host " [$res]" -ForegroundColor Green
            }
            Write-Host "✔ Debloat completed!" -ForegroundColor Green
        }
    }
    "2" {
        $confirm = Read-Host "Proceed with disabling $($detectedBloat.Count) packages? (y/N)"
        if ($confirm -eq "y" -or $confirm -eq "Y") {
            foreach ($pkg in $detectedBloat) {
                Write-Host "Disabling: $pkg ..." -NoNewline
                $res = & $ADB_BIN -s $TARGET_DEVICE shell "pm disable-user --user 0 $pkg"
                Write-Host " [$res]" -ForegroundColor Green
            }
            Write-Host "✔ Disable completed!" -ForegroundColor Green
        }
    }
    "3" {
        $customPkg = Read-Host "Enter package name to remove (e.g. com.example.app)"
        if ($protectedPkgs -contains $customPkg) {
            Write-Warning "BLOCKED: $customPkg is protected!"
        } elseif (-not [string]::IsNullOrWhiteSpace($customPkg)) {
            $res = & $ADB_BIN -s $TARGET_DEVICE shell "pm uninstall -k --user 0 $customPkg"
            Write-Host "$customPkg uninstall result: $res" -ForegroundColor Green
        }
    }
    "4" {
        $snapshots = Get-ChildItem -Directory (Join-Path $scriptDir "backups") -ErrorAction SilentlyContinue
        if (-not $snapshots) {
            Write-Warning "No snapshots found to restore."
        } else {
            Write-Host "Available Snapshots:"
            for ($i = 0; $i -lt $snapshots.Count; $i++) {
                Write-Host "  [$i] $($snapshots[$i].Name)"
            }
            $sIdx = Read-Host "Select snapshot index"
            if ($sIdx -match '^\d+$' -and [int]$sIdx -lt $snapshots.Count) {
                $chosen = $snapshots[[int]$sIdx]
                $uninstalledFile = Join-Path $chosen.FullName "uninstalled_packages.txt"
                if (Test-Path $uninstalledFile) {
                    $toReinstall = Get-Content $uninstalledFile
                    foreach ($line in $toReinstall) {
                        if ($line -match '^package:(.+)$') {
                            $p = $matches[1].Trim()
                            & $ADB_BIN -s $TARGET_DEVICE shell "cmd package install-existing $p" | Out-Null
                        }
                    }
                    Write-Host "✔ Re-enabled uninstalled packages from snapshot." -ForegroundColor Green
                }
            }
        }
    }
    default {
        Write-Host "Exiting." -ForegroundColor Gray
    }
}

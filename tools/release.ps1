#requires -Version 5.1
<#
.SYNOPSIS
    Build, tag and publish a Brain Storm release — the local twin of .github/workflows/release.yml.

.DESCRIPTION
    Produces the same assets the CI workflow does, so a release made offline is indistinguishable
    from one made on a runner:

        dist/Brain-Storm-<version>_consumer_<channel>.apk
        dist/Brain-Storm-<version>_managed_<channel>.apk
        dist/version.json      (read by the in-app updater)
        dist/SHA256SUMS

    The tag is v<versionName>-<channel>.<commitCount>. versionCode equals the commit count, so the
    working tree must be committed before running this (an uncommitted tree would ship a version
    whose APK can never be upgraded by the same tag).

.PARAMETER SkipPublish
    Build and checksum only; no tag, no GitHub release. Useful for smoke-testing a build.

.PARAMETER Notes
    Extra release notes appended to the generated body.
#>
[CmdletBinding()]
param(
    [switch]$SkipPublish,
    [string]$Notes = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    # ── Preconditions ────────────────────────────────────────────────────────────────────────
    if (git status --porcelain) {
        throw "Working tree is dirty. Commit first: versionCode is the commit count, so an uncommitted build cannot be upgraded by the release it produces."
    }
    if (-not (Test-Path 'keystore.properties') -or -not (Test-Path 'brainstorm-upload.jks')) {
        throw "Missing keystore.properties / brainstorm-upload.jks — refusing to publish unsigned APKs."
    }
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh CLI not found.' }

    $props = @{}
    Get-Content 'version.properties' | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        $props[$k.Trim()] = $v.Trim()
    }
    $version = $props['versionName']
    $channel = $props['channel']
    if (-not $version -or -not $channel) { throw 'version.properties must define versionName and channel.' }

    $code = [int](git rev-list --count HEAD)
    $tag = "v$version-$channel.$code"

    if (git rev-parse -q --verify "refs/tags/$tag" 2>$null) {
        throw "Tag $tag already exists. versionCode must increase, which means a new commit."
    }

    Write-Host "Releasing $tag (versionCode $code, channel $channel)" -ForegroundColor Cyan

    # ── Build ────────────────────────────────────────────────────────────────────────────────
    & .\gradlew.bat :app:testConsumerDebugUnitTest :app:assembleConsumerRelease :app:assembleManagedRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }

    # ── Collect ──────────────────────────────────────────────────────────────────────────────
    $dist = Join-Path $root 'dist'
    if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
    New-Item -ItemType Directory -Path $dist | Out-Null

    # apksigner lives in the newest build-tools under the SDK declared in local.properties.
    $sdkDir = $env:ANDROID_HOME
    if (-not $sdkDir -and (Test-Path 'local.properties')) {
        $line = Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) { $sdkDir = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
    }
    $apksigner = $null
    if ($sdkDir) {
        $apksigner = Get-ChildItem (Join-Path $sdkDir 'build-tools') -Directory -ErrorAction SilentlyContinue |
            Sort-Object { [version]$_.Name } -Descending |
            ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
    }
    if (-not $apksigner) { throw 'apksigner not found — cannot verify APK signatures.' }

    $apks = [ordered]@{}
    foreach ($flavour in 'consumer', 'managed') {
        $src = "app/build/outputs/apk/$flavour/release/app-$flavour-release.apk"
        if (-not (Test-Path $src)) { throw "Missing $src" }
        $name = "Brain-Storm-${version}_${flavour}_${channel}.apk"
        Copy-Item $src (Join-Path $dist $name)

        # A debug-signed APK would install here but could never be updated by a real release.
        $cert = (& $apksigner verify --print-certs (Join-Path $dist $name) 2>&1) -join "`n"
        if ($cert -match 'CN=Android Debug') { throw "$name is debug-signed. Check signingConfigs.release." }

        $hash = (Get-FileHash (Join-Path $dist $name) -Algorithm SHA256).Hash.ToLowerInvariant()
        $apks[$flavour] = [ordered]@{
            file   = $name
            sha256 = $hash
            size   = (Get-Item (Join-Path $dist $name)).Length
        }
    }

    $payload = [ordered]@{
        versionCode = $code
        versionName = $version
        channel     = $channel
        tag         = $tag
        publishedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        apks        = $apks
    }
    $json = $payload | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText((Join-Path $dist 'version.json'), $json, (New-Object System.Text.UTF8Encoding($false)))

    $sums = foreach ($flavour in 'consumer', 'managed') {
        $a = $apks[$flavour]
        "$($a.sha256)  $($a.file)"
    }
    [IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS'), ($sums -join "`n") + "`n", (New-Object System.Text.UTF8Encoding($false)))

    Write-Host "Assets in $dist" -ForegroundColor Green
    Get-ChildItem $dist | Select-Object Name, Length | Format-Table | Out-String | Write-Host

    if ($SkipPublish) {
        Write-Host 'SkipPublish set — tag and release not created.' -ForegroundColor Yellow
        return
    }

    # ── Tag + publish ────────────────────────────────────────────────────────────────────────
    git tag -a $tag -m "Brain Storm $version ($channel $code)"
    git push origin $tag

    $body = @"
Automated build of ``$(git rev-parse --short HEAD)`` (versionCode $code).

Install over an older Brain Storm of the same flavour; upgrades keep your frozen policy.

* ``Brain-Storm-${version}_consumer_${channel}.apk`` — normal install
* ``Brain-Storm-${version}_managed_${channel}.apk`` — Device Owner build
* ``version.json`` + ``SHA256SUMS`` — used by the in-app updater
"@
    if ($Notes) { $body += "`n$Notes" }

    $releaseArgs = @(
        'release', 'create', $tag,
        '--target', (git rev-parse HEAD),
        '--title', "Brain Storm $version ($channel $code)",
        '--notes', $body
    )
    if ($channel -eq 'beta') { $releaseArgs += '--prerelease' }
    $releaseArgs += (Get-ChildItem $dist | ForEach-Object { $_.FullName })

    & gh @releaseArgs
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed for $tag." }

    Write-Host "Published $tag" -ForegroundColor Green
}
finally {
    Pop-Location
}

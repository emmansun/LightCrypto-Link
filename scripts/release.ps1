# Release script for LightCrypto-Link (Windows PowerShell)
# Usage:
#   .\scripts\release.ps1 0.1.0             # create and push v0.1.0
#   .\scripts\release.ps1 v0.1.0            # same (v prefix is optional)
#   .\scripts\release.ps1 0.1.0 -Recreate   # delete existing tag first, then recreate
param(
    [Parameter(Position=0)]
    [string]$Version,

    [switch]$Recreate
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrEmpty($Version)) {
    Write-Host "Usage: .\release.ps1 <version> [-Recreate]"
    Write-Host "  version   : release version (e.g. 0.1.0 or v0.1.0)"
    Write-Host "  -Recreate : delete existing remote/local tag before creating"
    exit 1
}

# Strip leading 'v' if present
$Version = $Version -replace '^v', ''
$Tag = "v$Version"

Write-Host "==> Preparing release $Tag"

if ($Recreate) {
    # Delete local tag if exists
    $localTags = git tag -l
    if ($localTags -contains $Tag) {
        Write-Host "  Deleting local tag $Tag..."
        git tag -d $Tag
    }
    # Delete remote tag if exists
    $remoteTags = git ls-remote --tags origin
    if ($remoteTags -match "refs/tags/$Tag$") {
        Write-Host "  Deleting remote tag $Tag..."
        git push origin ":refs/tags/$Tag"
    }
}

# Create tag
Write-Host "  Creating tag $Tag..."
git tag $Tag

# Push tag
Write-Host "  Pushing tag $Tag to origin..."
git push origin $Tag

Write-Host ""
Write-Host "==> Release $Tag triggered!"
Write-Host "    Monitor: https://github.com/emmansun/LightCrypto-Link/actions/workflows/release.yml"

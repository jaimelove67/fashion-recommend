$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$extensionOutput = Join-Path $projectRoot 'output/zhiji-try-on-extension'
$downloadOutput = Join-Path $projectRoot 'frontend/public/downloads'
New-Item -ItemType Directory -Force -Path $extensionOutput, $downloadOutput | Out-Null
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'browser-extension') -File | Copy-Item -Destination $extensionOutput -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'frontend/src/utils/liveTryOn.js') -Destination (Join-Path $extensionOutput 'liveTryOn.js') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'frontend/src/utils/tryOnImages.js') -Destination (Join-Path $extensionOutput 'images.js') -Force
Compress-Archive -Path (Join-Path $extensionOutput '*') -DestinationPath (Join-Path $downloadOutput 'zhiji-try-on-extension.zip') -Force
Write-Output "Extension: $extensionOutput"
Write-Output "Download: $(Join-Path $downloadOutput 'zhiji-try-on-extension.zip')"

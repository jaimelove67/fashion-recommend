[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$outputRoot = Join-Path $projectRoot 'output/cloud-deploy'
$packageId = 'fashion-cloud-deploy-{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), ([guid]::NewGuid().ToString('N').Substring(0, 8))
$stagingRoot = Join-Path $outputRoot "$packageId/staging"
$archivePath = Join-Path $outputRoot "$packageId.tar.gz"
$manifestPath = Join-Path $outputRoot "$packageId.files.txt"
$checksumPath = "$archivePath.sha256"

if (-not (Get-Command tar -ErrorAction SilentlyContinue)) {
    throw 'tar is required. Run this script with Windows tar or another tar available on PATH.'
}

# Copy only deployment inputs from the working tree, including uncommitted changes.
# Never copy a repository root, upload directory, data volume or local environment.
$requiredFiles = @(
    '.gitignore'
    'docker-compose.yml'
    'docker-compose.cloud.yml'
    'docker-compose.cloud.tls.yml'
    '.env.cloud.example'
    'docker/cloud/nginx.conf'
    'docker/cloud/nginx.tls.conf'
    'docker/cloud/locations.conf'
    'scripts/cloud-init-env.py'
    'scripts/cloud-deploy.sh'
    'scripts/open-cloud-tunnel.ps1'
    'scripts/package-cloud-deploy.ps1'
    'backend/Dockerfile'
    'backend/.dockerignore'
    'backend/pom.xml'
    'backend/src/main/resources/application.yml'
    'backend/src/main/resources/reference_photo/model-male.png'
    'backend/src/main/resources/reference_photo/model-female.png'
    'frontend/Dockerfile'
    'frontend/.dockerignore'
    'frontend/package.json'
    'frontend/package-lock.json'
    'frontend/index.html'
    'frontend/vite.config.js'
    'frontend/nginx.conf'
    'frontend/dev/trendImageProxy.mjs'
    'frontend/public/assets/zhiji-logo.png'
    'frontend/public/assets/profile-portrait.png'
    'frontend/public/assets/look-everyday-flatlay.png'
    'frontend/public/assets/look-commute-flatlay.png'
    'frontend/public/assets/look-color.jpg'
    'frontend/public/assets/look-urban.jpg'
    'frontend/public/assets/look-tailoring.jpg'
    'frontend/public/assets/home-look-details.webp'
    'frontend/public/assets/home-look-commute.webp'
    'frontend/public/assets/home-look-casual.webp'
    'frontend/public/prototypes/zhiji-fish/assets/fish-atlas.png'
    'frontend/public/prototypes/zhiji-fish/assets/original-logo.png'
    'frontend/public/downloads/zhiji-try-on-extension.zip'
    'tools/trend-collector/Dockerfile'
    'tools/trend-collector/.dockerignore'
    'tools/trend-collector/requirements-public.txt'
    'tools/trend-collector/content_scope.py'
    'tools/trend-collector/collect_public.py'
    'tools/trend-collector/daily_collector.py'
)

$requiredTrees = @(
    @{ Path = 'backend/src/main/java'; Extensions = @('.java') }
    @{ Path = 'backend/src/main/resources/db/migration'; Extensions = @('.sql') }
    @{ Path = 'frontend/src'; Extensions = @('.vue', '.js', '.mjs', '.css', '.json') }
    @{ Path = 'frontend/public/assets/trend-pieces'; Extensions = @('.png', '.jpg', '.jpeg', '.webp') }
    @{ Path = 'frontend/public/assets/trend-analysis'; Extensions = @('.png', '.jpg', '.jpeg', '.webp') }
    @{ Path = 'docker/postgres/init'; Extensions = @('.sql') }
)

function Assert-SafeSource([string] $RelativePath) {
    $normalized = $RelativePath.Replace('\', '/')
    $segments = $normalized.Split('/')
    $leaf = $segments[-1]
    if ($normalized.StartsWith('/') -or $normalized.Contains(':') -or $segments -contains '..') {
        throw "Invalid package path: $RelativePath"
    }
    if ($leaf -like '.env*' -and $normalized -ne '.env.cloud.example') {
        throw "Local environment files must not be packaged: $RelativePath"
    }
    if ($segments | Where-Object { $_ -in @('.git', '.codex', '.agents', 'node_modules', 'target', 'dist', 'output', 'outputs', 'uploads', '__pycache__', '.venv') }) {
        throw "Local data or build output must not be packaged: $RelativePath"
    }
    if ($leaf -match '(?i)(\.(pem|key|pfx|p12|crt|cer|jks|keystore)|\.env|\.log|\.sql\.gz)$' -or $leaf -match '^id_(rsa|ecdsa|ed25519)(\.|$)') {
        throw "Credential, certificate or local output must not be packaged: $RelativePath"
    }
    # Reject symlinks/junctions at every level so allowlisted paths cannot escape the tree.
    $sourcePath = $projectRoot
    foreach ($segment in $segments) {
        $sourcePath = Join-Path $sourcePath $segment
        $item = Get-Item -LiteralPath $sourcePath -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Linked paths must not be packaged: $RelativePath"
        }
    }
}

$packageFiles = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath) -PathType Leaf)) {
        throw "Required deployment file is missing: $relativePath"
    }
    Assert-SafeSource $relativePath
    $packageFiles.Add($relativePath)
}

foreach ($tree in $requiredTrees) {
    $treeRoot = Join-Path $projectRoot $tree.Path
    if (-not (Test-Path -LiteralPath $treeRoot -PathType Container)) {
        throw "Required source directory is missing: $($tree.Path)"
    }
    Assert-SafeSource $tree.Path
    $treeFiles = @(Get-ChildItem -LiteralPath $treeRoot -Recurse -Force -File | Where-Object { $_.Extension -in $tree.Extensions })
    if ($treeFiles.Count -eq 0) {
        throw "Required source directory contains no deployable files: $($tree.Path)"
    }
    foreach ($file in $treeFiles) {
        $relativePath = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        Assert-SafeSource $relativePath
        $packageFiles.Add($relativePath)
    }
}

$deploymentDoc = 'docs/cloud-deployment.md'
if (Test-Path -LiteralPath (Join-Path $projectRoot $deploymentDoc) -PathType Leaf) {
    Assert-SafeSource $deploymentDoc
    $packageFiles.Add($deploymentDoc)
} else {
    Write-Warning "$deploymentDoc is missing; configuration and source files will still be packaged."
}

$selectedFiles = @($packageFiles | Sort-Object -Unique)
New-Item -ItemType Directory -Path $stagingRoot -Force | Out-Null
foreach ($relativePath in $selectedFiles) {
    $destination = Join-Path $stagingRoot $relativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot $relativePath) -Destination $destination
}

# Leave staging and a plain file manifest available for review; remove nothing.
[IO.File]::WriteAllLines($manifestPath, [string[]] $selectedFiles, [Text.UTF8Encoding]::new($false))
& tar -czf $archivePath -C $stagingRoot .
if ($LASTEXITCODE -ne 0) {
    throw "tar failed with exit code $LASTEXITCODE. Staging remains at $stagingRoot"
}
$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($checksumPath, "$archiveHash  $(Split-Path -Leaf $archivePath)`n", [Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    Archive = $archivePath
    SHA256 = $archiveHash
    Manifest = $manifestPath
    Checksum = $checksumPath
    Staging = $stagingRoot
    Files = $selectedFiles.Count
    ArchiveBytes = (Get-Item -LiteralPath $archivePath).Length
}

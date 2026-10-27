[CmdletBinding()]
param(
    [string] $Server = '193.112.24.191',
    [ValidateRange(1, 65535)]
    [int] $LocalPort = 18090,
    [ValidateRange(1, 65535)]
    [int] $RemotePort = 80,
    [switch] $OpenBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$sshDirectory = Join-Path $projectRoot '.codex_tmp/cloud-deploy/ssh'
$identityFile = Join-Path $sshDirectory 'server-key'
$knownHosts = Join-Path $sshDirectory 'known_hosts'
if (-not (Test-Path -LiteralPath $identityFile) -or -not (Test-Path -LiteralPath $knownHosts)) {
    throw 'The prepared SSH key or verified host record is missing. Restore the local SSH connection setup first.'
}
if (Get-NetTCPConnection -State Listen -LocalPort $LocalPort -ErrorAction SilentlyContinue) {
    throw "Local port $LocalPort is already in use. An existing project tunnel may already be running."
}
$sshArguments = @(
    '-i', ('"{0}"' -f $identityFile),
    '-N', '-L', "127.0.0.1:${LocalPort}:127.0.0.1:${RemotePort}",
    '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes',
    '-o', 'StrictHostKeyChecking=yes', '-o', ('UserKnownHostsFile="{0}"' -f $knownHosts),
    '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30',
    '-o', 'ServerAliveCountMax=3', "root@$Server"
)
$sshProcess = Start-Process -FilePath (Get-Command ssh).Source -ArgumentList $sshArguments `
    -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $sshDirectory 'tunnel.log')
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 250
    if ($sshProcess.HasExited) {
        throw 'The SSH tunnel could not start. See .codex_tmp/cloud-deploy/ssh/tunnel.log.'
    }
    if (Get-NetTCPConnection -State Listen -LocalPort $LocalPort -ErrorAction SilentlyContinue) {
        $url = "http://127.0.0.1:$LocalPort"
        try {
            $loginProbe = Invoke-RestMethod -Uri "$url/api/v1/auth/csrf" `
                -Headers @{ Accept = 'application/json' } -TimeoutSec 8
            if ($loginProbe.code -ne 0 -or -not $loginProbe.data.token -or -not $loginProbe.data.headerName) {
                throw 'The login endpoint did not return a valid session response.'
            }
        } catch {
            Stop-Process -Id $sshProcess.Id -ErrorAction SilentlyContinue
            throw "SSH connected, but the website login service is unavailable. Check the server listener and -RemotePort (currently $RemotePort)."
        }
        if ($OpenBrowser) { Start-Process $url }
        Write-Output "Cloud project tunnel is ready: $url (process $($sshProcess.Id))"
        return
    }
}
Stop-Process -Id $sshProcess.Id -ErrorAction SilentlyContinue
throw 'Timed out starting the SSH tunnel.'

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('SignImage', 'SignInstaller', 'VerifyImage')]
    [string] $Operation,
    [string] $Image = ''
)

# Called only by sign_distribution.py inside its private TemporaryDirectory.
# Never import an exportable key into a persistent Windows certificate store.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$certificate = $null

function Require([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Certificate-Hash($Cert) {
    return $Cert.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant()
}

function Invoke-SignTool([string[]] $Arguments) {
    # ArgumentList preserves passwords containing spaces or punctuation without
    # shell interpolation. Neither argv nor the raw tool output reaches CI logs.
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $script:signTool
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    $started = $false
    try {
        $started = $process.Start()
        Require $started 'Could not start the signing verifier'
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        Require ($process.WaitForExit(180000)) 'Signing tool timed out'
        $output = $stdout.GetAwaiter().GetResult()
        $errors = $stderr.GetAwaiter().GetResult()
        Require (($output.Length + $errors.Length) -le 16777216) 'Signing output exceeds its bound'
        Require ($process.ExitCode -eq 0) 'Signing or independent verification failed (private output withheld)'
    } finally {
        if ($started -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
    }
}

function Verify-Signature([string] $Path, [bool] $Owned) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    Require ($signature.Status -eq 'Valid') 'Invalid final Authenticode signature'
    Require ($null -ne $signature.TimeStamperCertificate) 'Authenticode timestamp is required'
    Require ($null -ne $signature.SignerCertificate) 'Signer certificate is missing'
    if ($Owned) {
        Require ((Certificate-Hash $signature.SignerCertificate) -ceq $env:GH_DIST_CERT_SHA256) 'Unexpected publisher certificate'
    }
    Invoke-SignTool @('verify', '/pa', '/all', '/v', '/tw', $Path)
}

function Sign-Owned([string] $Path) {
    $signature = Get-AuthenticodeSignature -LiteralPath $Path
    if ($signature.Status -eq 'Valid') {
        # Never replace a valid vendor signature or a partially signed artifact.
        Verify-Signature $Path $true
        return
    }
    Require ($signature.Status -eq 'NotSigned') 'Refusing to repair an invalid or foreign native signature'
    Invoke-SignTool @('sign', '/f', $script:pfx, '/p', $env:GH_DIST_WINDOWS_PFX_PASSWORD,
                     '/fd', 'SHA256', '/tr', 'https://timestamp.digicert.com', '/td', 'SHA256', $Path)
    Verify-Signature $Path $true
}

function Inspect-Image([string] $Root, [bool] $Sign) {
    $rootPath = [System.IO.Path]::GetFullPath($Root)
    Require (Test-Path -LiteralPath $rootPath -PathType Container) 'Application image is missing'
    $files = @(Get-ChildItem -LiteralPath $rootPath -Recurse -File | Where-Object { $_.Extension -in @('.exe', '.dll') })
    Require ($files.Count -ge 2 -and $files.Count -lt 1000) 'Native Windows hierarchy is incomplete or excessive'
    $ownedFound = @()
    foreach ($file in $files) {
        Require (($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0) 'Redirected native payload'
        $relative = [System.IO.Path]::GetRelativePath($rootPath, $file.FullName).Replace('\', '/')
        $owned = $relative -cin @('Parlor.exe', 'app/skiko-windows-x64.dll')
        if ($owned) { $ownedFound += $relative }
        if ($Sign -and $owned) { Sign-Owned $file.FullName } else { Verify-Signature $file.FullName $owned }
    }
    Require ($ownedFound.Count -eq 2 -and $ownedFound -ccontains 'Parlor.exe' -and
             $ownedFound -ccontains 'app/skiko-windows-x64.dll') 'Publisher-owned native coverage differs'
    $native = Join-Path $rootPath 'app/skiko-windows-x64.dll'
    $hash = (Get-FileHash -LiteralPath $native -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksum = "$native.sha256"
    if ($Sign) {
        [System.IO.File]::WriteAllText($checksum, $hash, [System.Text.UTF8Encoding]::new($false))
    } else {
        Require ((Get-Content -LiteralPath $checksum -Raw).Trim() -ceq $hash) 'Signed native checksum differs'
    }
}

try {
    Require $IsWindows 'A native Windows runner is required'
    Require ($env:GH_DIST_MODE -ceq 'candidate') 'Only protected validation-only candidates can sign'
    Require ($env:GH_DIST_WINDOWS_PFX_APPROVED_SHA -ceq $env:GITHUB_SHA) 'Exportable-PFX authorization differs from source'
    Require ($env:GH_DIST_CERT_SHA256 -cmatch '^[0-9a-f]{64}$') 'Missing protected certificate pin'
    $root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
    $private = [System.IO.Path]::GetFullPath($env:GH_DIST_PRIVATE_DIRECTORY)
    Require ((Split-Path $private -Leaf) -clike 'parlor-private-signing-*') 'Unexpected private signing directory'
    $script:pfx = Join-Path $private 'publisher.pfx'
    Require (Test-Path -LiteralPath $script:pfx -PathType Leaf) 'Missing ephemeral signing identity'
    $tools = @(Get-ChildItem -Path "${env:ProgramFiles(x86)}/Windows Kits/10/bin/*/x64/signtool.exe" | Sort-Object FullName -Descending)
    Require ($tools.Count -gt 0) 'Windows SDK signing tool is unavailable'
    $script:signTool = $tools[0].FullName
    $toolSignature = Get-AuthenticodeSignature -LiteralPath $script:signTool
    Require ($toolSignature.Status -eq 'Valid' -and $toolSignature.SignerCertificate.Subject -match 'CN=Microsoft') 'Untrusted Windows SDK tool'
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        $script:pfx, $env:GH_DIST_WINDOWS_PFX_PASSWORD,
        [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    Require ($certificate.HasPrivateKey) 'PFX has no matching private key'
    Require ((Certificate-Hash $certificate) -ceq $env:GH_DIST_CERT_SHA256) 'PFX certificate differs from the protected pin'
    Require ($certificate.NotBefore.ToUniversalTime() -le [DateTime]::UtcNow -and
             $certificate.NotAfter.ToUniversalTime() -gt [DateTime]::UtcNow.AddDays(30)) 'Signing certificate is not currently release-valid'
    $eku = @($certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.37' })
    Require ($eku.Count -eq 1 -and '1.3.6.1.5.5.7.3.3' -in $eku[0].EnhancedKeyUsages.Value) 'Code Signing EKU is required'
    $chain = [System.Security.Cryptography.X509Certificates.X509Chain]::new()
    try {
        $chain.ChainPolicy.RevocationMode = [System.Security.Cryptography.X509Certificates.X509RevocationMode]::Online
        $chain.ChainPolicy.UrlRetrievalTimeout = [TimeSpan]::FromSeconds(30)
        Require ($chain.Build($certificate)) 'Publisher certificate chain/revocation validation failed'
    } finally { $chain.Dispose() }
    $sourceImage = Join-Path $root 'composeApp/build/compose/binaries/main/app/Parlor'
    if ($Operation -eq 'SignImage') {
        Inspect-Image $sourceImage $true
        Inspect-Image $sourceImage $false
    } elseif ($Operation -eq 'VerifyImage') {
        Require (-not [string]::IsNullOrWhiteSpace($Image)) 'Installed-image path is required'
        Inspect-Image $Image $false
    } else {
        $installers = @(Get-ChildItem -LiteralPath (Join-Path $root 'build/github-distribution/work') -Filter 'Parlor-*-windows-x64.msi' -File)
        Require ($installers.Count -eq 1) 'Expected one frozen-version MSI'
        Sign-Owned $installers[0].FullName
    }
} catch {
    # PowerShell's normal ErrorRecord can include invocation arguments. Do not
    # print it, $_, its InvocationInfo, or a transcript containing credentials.
    [Console]::Error.WriteLine('Windows signing/verification failed; private diagnostics withheld.')
    exit 2
} finally {
    if ($null -ne $certificate) { $certificate.Dispose() }
}

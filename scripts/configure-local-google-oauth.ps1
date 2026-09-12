$ErrorActionPreference = "Stop"

$clientId = Read-Host "Google OAuth client ID"
if ([string]::IsNullOrWhiteSpace($clientId)) {
    throw "Google OAuth client ID is required."
}

$secureSecret = Read-Host "Google OAuth client secret" -AsSecureString
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)

try {
    $clientSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    [Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_ID", $clientId.Trim(), "User")
    [Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_SECRET", $clientSecret, "User")
    [Environment]::SetEnvironmentVariable("GOOGLE_REDIRECT_URI", "http://localhost:8081/login/oauth2/code/google", "User")
}
finally {
    if ($secretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    }
}

Write-Host "Google OAuth credentials saved to the Windows user environment."
Write-Host "Start a new PowerShell session before launching the backend."
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaHome = Join-Path $repoRoot ".jdk\jdk-21.0.10"
$maven = Join-Path $repoRoot ".maven\maven-3.9.16\bin\mvn.cmd"

$clientId = [Environment]::GetEnvironmentVariable("GOOGLE_CLIENT_ID", "User")
$clientSecret = [Environment]::GetEnvironmentVariable("GOOGLE_CLIENT_SECRET", "User")
if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($clientSecret)) {
    throw "Google OAuth credentials are missing. Run scripts\configure-local-google-oauth.ps1 first."
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SERVER_PORT = "8081"
$env:GOOGLE_CLIENT_ID = $clientId
$env:GOOGLE_CLIENT_SECRET = $clientSecret
$env:GOOGLE_REDIRECT_URI = "http://localhost:8081/login/oauth2/code/google"
$env:FRONTEND_URL = "http://localhost:5173"

Set-Location (Join-Path $repoRoot "backend")
& $maven spring-boot:run
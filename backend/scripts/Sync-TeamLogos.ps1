param(
    [switch]$Force,
    [ValidateSet('Local', 'Hosted')][string]$Target = 'Local',
    [string]$ApiUrl,
    [ValidateRange(30, 600)][int]$HealthTimeoutSeconds = 240,
    [string]$Email
)
$ErrorActionPreference = 'Stop'

if (-not $ApiUrl) {
    $ApiUrl = if ($Target -eq 'Hosted') { $env:SUPERPONT_API_URL } else { 'http://localhost:8080' }
}
if (-not $ApiUrl) { throw 'Pentru Hosted setează SUPERPONT_API_URL sau transmite -ApiUrl https://serviciul.onrender.com.' }
$origin = [uri]$ApiUrl
if ($origin.Scheme -notin @('http', 'https') -or -not $origin.IsAbsoluteUri -or $origin.UserInfo -or $origin.AbsolutePath -ne '/' -or $origin.Query -or $origin.Fragment) {
    throw 'ApiUrl trebuie să fie o origine HTTP(S) fără cale, query sau credențiale.'
}
if (-not $origin.IsLoopback -and $origin.Scheme -ne 'https') { throw 'Backend-ul găzduit necesită HTTPS.' }
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$base = $ApiUrl.TrimEnd('/') + '/api'
$configurationHint = if ($origin.IsLoopback) {
    'Configurează cheia în fișierul .env din rădăcina proiectului, apoi rulează podman compose up -d --force-recreate backend și reîncearcă importul.'
} else {
    'Configurează variabila SPORTSTATS_API_KEY în mediul backend-ului găzduit și repornește serviciul.'
}
$loggedIn = $false
Write-Host "Se pregătește preluarea logo-urilor la $ApiUrl."
Write-Host 'Se verifică pornirea backend-ului...'
$ready = $false
$deadline = [DateTime]::UtcNow.AddSeconds($HealthTimeoutSeconds)
while ([DateTime]::UtcNow -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri ($ApiUrl.TrimEnd('/') + '/actuator/health') -TimeoutSec 15
        if ($health.status -eq 'UP') { $ready = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    $hint = if ($origin.IsLoopback) { 'Verifică podman compose logs backend.' } else { 'Verifică Events și Logs în Render; serviciul poate fi suspendat sau în curs de pornire.' }
    throw "Backend-ul nu a devenit disponibil în $HealthTimeoutSeconds secunde. $hint"
}
if (-not $Email) { $Email = Read-Host 'Email administrator Super Pont' }
$secret = Read-Host 'Parola administratorului Super Pont' -AsSecureString
function Get-CsrfHeaders {
    $csrf = Invoke-RestMethod -Uri "$base/auth/csrf" -WebSession $session -TimeoutSec 15
    return @{ $csrf.headerName = $csrf.token }
}
try {
    $plain = (New-Object System.Management.Automation.PSCredential($Email, $secret)).GetNetworkCredential().Password
    $login = @{ email = $Email.Trim().ToLowerInvariant(); password = $plain } | ConvertTo-Json
    $account = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -WebSession $session -Headers (Get-CsrfHeaders) -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($login))
    $loggedIn = -not [string]::IsNullOrWhiteSpace($account.role)
    if ($account.role -ne 'ADMIN') { throw 'Este necesar un cont ADMIN cu adresa de email confirmată.' }
    $forceValue = $Force.IsPresent.ToString().ToLowerInvariant()
    Write-Host 'Autentificare ADMIN reușită. Se preiau logo-urile...'
    $summary = Invoke-RestMethod -Method Post -Uri "$base/admin/sportstats/logos?force=$forceValue" -WebSession $session -Headers (Get-CsrfHeaders) -ContentType 'application/json' -TimeoutSec 900
    Write-Host "Logo-uri: $($summary.downloaded) descărcate, $($summary.cached) în cache, $($summary.missing) absente, $($summary.errors) erori."

    $summary
} catch {
    $failure = $_
    $problem = $null
    if ($failure.ErrorDetails.Message) {
        try { $problem = $failure.ErrorDetails.Message | ConvertFrom-Json } catch {}
    }
    if (-not $problem -and $failure.Exception.Response) {
        try {
            $reader = New-Object IO.StreamReader($failure.Exception.Response.GetResponseStream())
            try { $problem = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        } catch {}
    }
    $message = switch ($problem.code) {
        'API_KEY_MISSING' { "SPORTSTATS_API_KEY lipsește din backend. $configurationHint" }
        'API_AUTHENTICATION' { 'Cheia SportStats este invalidă sau planul nu permite accesul. Verifică configurarea furnizorului.' }
        'PAYMENT_REQUIRED' { 'SportStats a solicitat plată. Importul a fost oprit fără nicio plată automată.' }
        'API_RATE_LIMITED' { 'Limita SportStats a fost atinsă. Respectă intervalul Retry-After și reîncearcă mai târziu.' }
        'RETRY_AFTER' { 'Importul este temporar limitat. Respectă intervalul Retry-After și reîncearcă mai târziu.' }
        default {
            if ($failure.Exception.Response -and [int]$failure.Exception.Response.StatusCode -eq 401) { 'Emailul sau parola administratorului sunt incorecte.' }
            elseif ($problem.detail) { [string]$problem.detail }
            elseif (-not $failure.Exception.Response) { $failure.Exception.Message }
            else { 'Importul a eșuat. Verifică disponibilitatea backend-ului și configurarea SportStats.' }
        }
    }
    throw [InvalidOperationException]::new($message)
} finally {
    $plain = $null
    $login = $null
    $secret = $null
    if ($loggedIn) {
        try { Invoke-RestMethod -Method Post -Uri "$base/auth/logout" -WebSession $session -Headers (Get-CsrfHeaders) -TimeoutSec 15 | Out-Null } catch {}
    }
}

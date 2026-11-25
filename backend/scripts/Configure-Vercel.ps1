param([Parameter(Mandatory = $true)][string]$BackendUrl)
$ErrorActionPreference = 'Stop'
$origin = [uri]$BackendUrl
if (-not $origin.IsAbsoluteUri -or $origin.Scheme -ne 'https' -or -not $origin.Host -or
    $origin.UserInfo -or $origin.AbsolutePath -ne '/' -or $origin.Query -or $origin.Fragment) {
    throw 'BackendUrl trebuie să fie originea HTTPS a serviciului Render, fără cale sau credențiale.'
}
$configPath = Join-Path $PSScriptRoot '../../frontend/vercel.json'
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$config.rewrites[0].destination = $BackendUrl.TrimEnd('/') + '/api/:path*'
$json = $config | ConvertTo-Json -Depth 10
[IO.File]::WriteAllText([IO.Path]::GetFullPath($configPath), $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
Write-Host 'frontend/vercel.json actualizat local. VITE_API_URL în Vercel trebuie să fie originea HTTPS a frontendului (API prin proxy).'
Write-Host 'Publicarea se face separat, numai când ești pregătită.'

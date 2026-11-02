$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$config = if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env.local')) {
    Join-Path $PSScriptRoot '.env.local'
} elseif (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env')) {
    Join-Path $PSScriptRoot '.env'
} else {
    $null
}

if (-not $config) {
    throw 'Create .env.local or .env from .env.example with your database connection details before running this check.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $config) {
    if ($line -match '^\s*(DB_JDBC_URL|DB_USER|DB_PASSWORD)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim('"''')
    }
}

foreach ($name in @('DB_JDBC_URL', 'DB_USER', 'DB_PASSWORD')) {
    if (-not $settings.ContainsKey($name)) { throw "Missing required setting: $name" }
}

$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    
    New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot 'backend/target/tools') | Out-Null
    mvn -q dependency:copy '-Dartifact=org.postgresql:postgresql:42.7.4' '-DoutputDirectory=target/tools' -f backend/pom.xml
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the PostgreSQL JDBC driver.' }
    
    java --class-path "backend/target/tools/postgresql-42.7.4.jar;ops" ops/DatabasePreflight.java
    if ($LASTEXITCODE -ne 0) { throw 'Database preflight check failed.' }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    $settings.Clear()
}

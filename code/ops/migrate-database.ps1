$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '../.env.local'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .env.local file'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(DB_JDBC_URL|DB_USER|DB_PASSWORD)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim('"''')
    }
}

$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    
    java --class-path "../backend/target/tools/postgresql-42.7.4.jar;." DatabaseMigrate.java
    if ($LASTEXITCODE -ne 0) { throw 'Database migration failed.' }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    $settings.Clear()
}

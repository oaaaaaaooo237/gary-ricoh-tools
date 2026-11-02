$ErrorActionPreference='Stop'
$base=$PSScriptRoot
$dependencies=Join-Path $base 'dependencies'
New-Item -ItemType Directory -Force $dependencies|Out-Null
foreach($entry in (Get-Content -Raw (Join-Path $base 'dependencies.lock.json')|ConvertFrom-Json)){
 $target=Join-Path $dependencies $entry.name
 if(!(Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256){Invoke-WebRequest -Uri $entry.url -OutFile $target}
 if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256){throw "Dependency hash mismatch: $($entry.name)"}
 Write-Output "$($entry.name) verified"
}

param([string]$Archive,[string]$NodeDirectory)
$ErrorActionPreference='Stop'
$deps=Join-Path $PSScriptRoot 'dependencies'
$output=Join-Path $deps 'effect-converter'
New-Item -ItemType Directory -Force $deps,$output|Out-Null
if(!$Archive){
 $Archive=Join-Path $deps 'GRKit-1.3.5-site.tar.gz'
 if(!(Test-Path -LiteralPath $Archive)){Invoke-WebRequest 'https://github.com/ahonn/GRKit/releases/download/v1.3.5/GRKit-1.3.5-site.tar.gz' -OutFile $Archive}
}
if((Get-FileHash -LiteralPath $Archive).Hash.ToLowerInvariant() -ne '160be4ddc00645e90e6b10e61f4a3b9164b02d01c5ab589ad14f7ff71dc39f13'){throw 'GRKit release hash mismatch'}
$stage=Join-Path $deps 'effect-stage'
New-Item -ItemType Directory -Force $stage|Out-Null
& tar -xf $Archive -C $stage './assets/grkit_wasm-Bv1y84tD.js' './assets/grkit_wasm_bg-Ddg15dz0.wasm' './effects' './THIRD_PARTY_LICENSES.txt'
if($LASTEXITCODE){throw 'GRKit converter extraction failed'}
Copy-Item "$stage/assets/*" $output -Force
New-Item -ItemType Directory -Force "$output/catalog"|Out-Null
foreach($target in @('gr3-2.10','gr4-1.11','gr4-1.20','gr4-1.20-0528')){New-Item -ItemType Directory -Force "$output/catalog/$target"|Out-Null;Get-ChildItem -LiteralPath "$stage/effects/$target"|Copy-Item -Destination "$output/catalog/$target" -Recurse -Force}
Copy-Item -LiteralPath "$stage/THIRD_PARTY_LICENSES.txt" -Destination "$output/GRKit-THIRD_PARTY_LICENSES.txt" -Force
if($NodeDirectory){
 Copy-Item -LiteralPath (Join-Path $NodeDirectory 'node.exe') -Destination "$output/node.exe" -Force
 Copy-Item -LiteralPath (Join-Path $NodeDirectory 'LICENSE') -Destination "$output/Node-LICENSE.txt" -Force
}elseif(!(Test-Path -LiteralPath "$output/node.exe")){
 $nodeZip=Join-Path $deps 'node-v24.14.0-win-x64.zip'
 if(!(Test-Path -LiteralPath $nodeZip)){Invoke-WebRequest 'https://nodejs.org/dist/v24.14.0/node-v24.14.0-win-x64.zip' -OutFile $nodeZip}
 Add-Type -AssemblyName System.IO.Compression.FileSystem
 $zip=[IO.Compression.ZipFile]::OpenRead($nodeZip)
 try{foreach($name in @('node.exe','LICENSE')){
  $entry=$zip.GetEntry('node-v24.14.0-win-x64/'+$name)
  if(!$entry){throw 'Node archive incomplete'}
  $stream=$entry.Open();try{
   $target=Join-Path $output $(if($name -eq 'LICENSE'){'Node-LICENSE.txt'}else{$name})
   $file=[IO.File]::Create($target);try{$stream.CopyTo($file)}finally{$file.Dispose()}
  }finally{$stream.Dispose()}
 }}finally{$zip.Dispose()}
}
if((Get-FileHash "$output/node.exe").Hash.ToLowerInvariant() -ne '63c259c81e5d472b5f11c8d506070130cb04a1ecf84b80377a34ed6ec9048088'){throw 'Pinned Node binary hash mismatch'}
[IO.File]::WriteAllText("$output/package.json",'{"type":"module"}',[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath "$PSScriptRoot/resources/import-effect.mjs" -Destination "$output/import-effect.mjs" -Force
Write-Output 'Pinned offline effect converter prepared'

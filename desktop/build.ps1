param([switch]$CompileOnly)
$ErrorActionPreference='Stop'
$base=$PSScriptRoot
$root=Split-Path $base -Parent
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{Split-Path (Get-Command javac.exe).Source -Parent}
$build=Join-Path $base 'build'
$dist=Join-Path $base 'dist'
foreach($part in @('classes','input','input/assets','input/fx','input/legal')){New-Item -ItemType Directory -Force (Join-Path $build $part)|Out-Null}
& (Join-Path $base 'fetch.ps1')
$core=@('CameraConnectionState','CameraIdentity','CameraWorkflowPolicy','ConnectionPolicy','EndScreenPolicy','EnhancementBundle','EnhancementDeployment','EnhancementPolicy','EnhancementReport','Gr3JpegEncoder','Gr3Profile','Gr3SdPolicy','Gr3SdSession','SdCardDeployment','SdCardRootPolicy','SdReconciler','WorkflowSession','WorkflowStages')
$sources=@($core|ForEach-Object{Join-Path $root "app/src/local/grworld/studio/$_.java"})+@(Get-ChildItem (Join-Path $base 'src') -Recurse -Filter '*.java'|ForEach-Object FullName)
$classpath=Join-Path $base 'dependencies/json-20250517.jar'
$modulepath=Join-Path $base 'dependencies'
& "$java/javac.exe" -encoding UTF-8 --release 21 --module-path $modulepath --add-modules javafx.controls -cp $classpath -d "$build/classes" @sources
if($LASTEXITCODE){throw 'Desktop compilation failed'}
$images=@{
 'GB_HDF.jpg'='d4b8554a7b71094bd6f63d87d5113b8cfe9f4db5cd5ec5317b48244a3b0cbf96'
 'GB_GR4.jpg'='dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79'
 'gr3x-goodbye.jpg'='dff9ba0ad2c7ea2697b19470cf6c695337651ee0ef415a6d3359a7f318ff3c79'
 'gr3x-urban.jpg'='7a2154a6d24ed5e460d2a918783d593171e399562923da582801186098ffc89f'
}
foreach($name in $images.Keys){$file=Join-Path $root "private-assets/$name";if(!(Test-Path -LiteralPath $file) -or (Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $images[$name]){throw "Missing or invalid stock resource: $name"};Copy-Item -LiteralPath $file -Destination "$build/input/assets/$name"}
$packages=@{gr3='b97e9aa3817c2e9a1cc00d538c8f8a0df188c055674fe2a432f544a4a26071ae';gr3x='73efffa80db1078c09f727155a459724e4b6e92b8daa8fbca461bf5d1d5ffaf3';gr4='99401ae517bb4cf702a8a97852fedc0e13e22b899e96791ab84a47f00fb605a9'}
foreach($family in $packages.Keys){$file=Join-Path $root "dependencies/GRKit-1.3.5-$family.zip";if(!(Test-Path -LiteralPath $file) -or (Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $packages[$family]){throw "Fetch pinned GRKit resources first: $family"};Copy-Item $file "$build/input/assets/grkit-$family.zip"}
Copy-Item "$base/resources/*" "$build/classes/" -Recurse -Force
Copy-Item "$root/LICENSE" "$build/classes/LICENSE"
Copy-Item "$root/LICENSE" "$build/input/legal/LICENSE"
Copy-Item "$root/LICENSES/*" "$build/input/legal/"
Copy-Item "$base/legal/*" "$build/input/legal/"
foreach($jar in Get-ChildItem "$base/dependencies/javafx-*-win.jar"){Copy-Item $jar.FullName "$build/input/fx/"}
Copy-Item $classpath "$build/input/json-20250517.jar"
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($jar in Get-ChildItem "$base/dependencies/*.jar"){
 $zip=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
 try{foreach($entry in $zip.Entries){if($entry.FullName -match '(?i)(^|/)(LICENSE|NOTICE|COPYRIGHT)(\.[a-z]+)?$'){$stream=$entry.Open();try{$target=Join-Path "$build/input/legal" ($jar.BaseName+'-'+$entry.Name);$out=[IO.File]::Create($target);try{$stream.CopyTo($out)}finally{$out.Dispose()}}finally{$stream.Dispose()}}}}finally{$zip.Dispose()}
}
$manifest="Manifest-Version: 1.0`nMain-Class: local.grworld.studio.DesktopApp`nClass-Path: json-20250517.jar`n`n"
[IO.File]::WriteAllText("$build/MANIFEST.MF",$manifest,[Text.UTF8Encoding]::new($false))
& "$java/jar.exe" --create --file "$build/input/desktop.jar" --manifest "$build/MANIFEST.MF" -C "$build/classes" . -C "$build/input" assets
if($LASTEXITCODE){throw 'Desktop JAR failed'}
if($CompileOnly){Write-Output "$build/input/desktop.jar";return}
if(!(Test-Path "$build/runtime")){& "$java/jlink.exe" --add-modules java.se,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output "$build/runtime";if($LASTEXITCODE){throw 'Runtime packaging failed'}}
if(Test-Path "$dist/GaryRicohTools"){
 $resolved=[IO.Path]::GetFullPath("$dist/GaryRicohTools");$allowed=[IO.Path]::GetFullPath($dist)+[IO.Path]::DirectorySeparatorChar
 if(!$resolved.StartsWith($allowed)){throw 'Output path outside desktop dist'}
 Remove-Item -LiteralPath $resolved -Recurse -Force
}
& "$java/jpackage.exe" --type app-image --input "$build/input" --main-jar desktop.jar --main-class local.grworld.studio.DesktopApp --runtime-image "$build/runtime" --dest $dist --name GaryRicohTools --app-version 0.1.1 --vendor 'Gary Fun Apps' --java-options '--module-path=$APPDIR/fx' --java-options '--add-modules=javafx.controls'
if($LASTEXITCODE){throw 'Desktop image packaging failed'}
Write-Output "$dist/GaryRicohTools/GaryRicohTools.exe"

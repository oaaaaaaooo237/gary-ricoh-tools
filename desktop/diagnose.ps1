$ErrorActionPreference='Stop'
$base=$PSScriptRoot
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{Split-Path (Get-Command javac.exe).Source -Parent}
& (Join-Path $base 'build.ps1') -CompileOnly
$classes=Join-Path $base 'build/diagnostics'
New-Item -ItemType Directory -Force $classes|Out-Null
$classpath="$base/build/input/desktop.jar;$base/dependencies/json-20250517.jar"
$sources=@(Get-ChildItem "$base/tests" -Recurse -Filter '*.java'|ForEach-Object FullName)
& "$java/javac.exe" -encoding UTF-8 --release 21 --module-path "$base/dependencies" --add-modules javafx.controls -cp $classpath -d $classes @sources
if($LASTEXITCODE){throw 'Diagnostic compilation failed'}
& "$java/java.exe" --module-path "$base/dependencies" --add-modules javafx.controls -cp "$classes;$classpath" local.grworld.studio.DesktopDiagnostics
if($LASTEXITCODE){throw 'Backend diagnostics failed; this is not visual acceptance'}
& "$java/java.exe" --module-path "$base/dependencies" --add-modules javafx.controls -cp "$classes;$classpath" local.grworld.studio.EffectDiagnostics
if($LASTEXITCODE){throw 'Effect diagnostics failed; this is not visual/camera acceptance'}

# Релизная сборка MiniHome: aapt2 -> javac -> R8 (ужатие и переименование) -> zipalign -> apksigner.
# Отличие от build.ps1: вместо d8 используется R8 с правилами r8-rules.pro, поэтому APK меньше.
$ErrorActionPreference = "Stop"
$root = "C:\Users\Ja"
$jdk  = "$root\smart-tv-launcher\toolchain\jdk"
$sdk  = "$root\smart-tv-launcher\toolchain\sdk"
$bt   = (Get-ChildItem "$sdk\build-tools" | Sort-Object Name | Select-Object -Last 1).FullName
$jar  = "$sdk\platforms\android-28\android.jar"
$out  = "$PSScriptRoot\build-release"
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\classes","$out\dex","$out\gen" | Out-Null

& "$bt\aapt2.exe" compile --dir "$PSScriptRoot\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile" }

& "$bt\aapt2.exe" link -o "$out\unsigned.apk" -I $jar --manifest "$PSScriptRoot\AndroidManifest.xml" -R "$out\res.zip" --auto-add-overlay --java "$out\gen"
if ($LASTEXITCODE) { throw "aapt2 link" }

$srcs = Get-ChildItem -Recurse "$PSScriptRoot\src","$out\gen" -Filter *.java | ForEach-Object FullName
$ErrorActionPreference = "Continue"
& javac -encoding UTF-8 --release 8 -nowarn -classpath $jar -d "$out\classes" $srcs 2>&1 | ForEach-Object { "$_" }
$javacExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($javacExit) { throw "javac" }

# R8 живёт внутри d8.jar в build-tools: ужимает неиспользуемый код и переименовывает его.
$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName
& "$jdk\bin\java" -cp "$bt\lib\d8.jar" com.android.tools.r8.R8 --release --min-api 21 --lib $jar --output "$out\dex" --pg-conf "$PSScriptRoot\r8-rules.pro" $classes
if ($LASTEXITCODE) { throw "r8" }

Copy-Item "$out\unsigned.apk" "$out\with-dex.apk"
Push-Location "$out\dex"
& jar uf "$out\with-dex.apk" classes.dex
Pop-Location

& "$bt\zipalign.exe" -f 4 "$out\with-dex.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "zipalign" }

$ks = "$root\smart-tv-launcher\launcher\litelauncher.keystore"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:litelauncher --out "$PSScriptRoot\MiniHome-release.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }

Get-Item "$PSScriptRoot\MiniHome-release.apk" | Select-Object Name, Length
Get-ChildItem "$out\dex" | Select-Object Name, Length

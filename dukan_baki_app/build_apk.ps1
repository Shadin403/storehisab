$ErrorActionPreference = "Stop"

$baseDir = "D:\Sadhin\dukan_baki_app"
$buildTools = "$baseDir\build-tools-34\android-14"
$jdkBin = "D:\Program Files\Microsoft\jdk-21.0.2.13-hotspot\bin"

$javac = "$jdkBin\javac.exe"
$jar = "$jdkBin\jar.exe"
$keytool = "$jdkBin\keytool.exe"

$aapt2 = "$buildTools\aapt2.exe"
$zipalign = "$buildTools\zipalign.exe"
$apksigner = "$buildTools\apksigner.bat"
$r8Jar = "$baseDir\build_tools\r8.jar"
$androidJar = "$baseDir\build_tools\android.jar"

Write-Host "=== 1. Cleaning and preparing build folders ==="
$buildDir = "$baseDir\build"
if (Test-Path $buildDir) {
    Remove-Item -Recurse -Force $buildDir
}
New-Item -ItemType Directory -Path "$buildDir\compiled_res", "$buildDir\gen", "$buildDir\classes", "$buildDir\dex" -Force | Out-Null

Write-Host "=== 2. Checking/Generating Keystore ==="
$keystorePath = "$baseDir\dukan_baki.keystore"
if (-not (Test-Path $keystorePath)) {
    & $keytool -genkeypair -v -keystore $keystorePath -alias dukan -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 -dname "CN=Dukan, OU=Store, O=BakiKhata, L=Dhaka, ST=Dhaka, C=BD"
}

Write-Host "=== 3. Compiling Android Resources with AAPT2 ==="
& $aapt2 compile --dir "$baseDir\src\main\res" -o "$buildDir\compiled_res.zip"

Write-Host "=== 4. Linking Resources with AAPT2 (SDK 21-34) ==="
& $aapt2 link -I $androidJar `
    --manifest "$baseDir\src\main\AndroidManifest.xml" `
    --min-sdk-version 21 `
    --target-sdk-version 34 `
    --version-code 1 `
    --version-name "1.0" `
    -o "$buildDir\unaligned.apk" `
    --java "$buildDir\gen" `
    "$buildDir\compiled_res.zip" `
    --auto-add-overlay

Write-Host "=== 5. Compiling Java Code ==="
$javaFiles = Get-ChildItem -Path "$baseDir\src\main\java", "$buildDir\gen" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& $javac -source 8 -target 8 -g:none -cp $androidJar -d "$buildDir\classes" $javaFiles

Write-Host "=== 6. Generating DEX with D8 ==="
$classFiles = Get-ChildItem -Path "$buildDir\classes" -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
java -cp $r8Jar com.android.tools.r8.D8 --output "$buildDir\dex" --lib $androidJar --min-api 21 $classFiles

if (-not (Test-Path "$buildDir\dex\classes.dex")) {
    throw "classes.dex was not generated!"
}

Write-Host "=== 7. Packaging classes.dex and assets ==="
$savedLocation = Get-Location
Set-Location "$buildDir\dex"
& $jar -uf "$buildDir\unaligned.apk" classes.dex

Set-Location "$baseDir\src\main"
& $jar -uf "$buildDir\unaligned.apk" assets
Set-Location $savedLocation

Write-Host "=== 8. Running Zipalign (4-byte alignment) ==="
$alignedApk = "$buildDir\aligned.apk"
& $zipalign -f -p 4 "$buildDir\unaligned.apk" $alignedApk

Write-Host "=== 9. Signing APK with apksigner (v1, v2, v3 schemes) ==="
$finalApk = "$buildDir\DukanBakiKhata.apk"
cmd /c "$apksigner" sign --ks "$keystorePath" --ks-pass pass:123456 --key-pass pass:123456 --ks-key-alias dukan --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$finalApk" "$alignedApk"

Write-Host "=== 10. Verifying Signature Schemes ==="
cmd /c "$apksigner" verify --verbose "$finalApk"

Write-Host "=== 11. Publishing APK ==="
Copy-Item "$finalApk" "$baseDir\DukanBakiKhata.apk" -Force
Copy-Item "$finalApk" "D:\Sadhin\DukanBakiKhata.apk" -Force

Write-Host "=== COMPLETED SUCCESSFULLY! ==="
Get-Item "D:\Sadhin\DukanBakiKhata.apk" | Select-Object Name, Length, LastWriteTime

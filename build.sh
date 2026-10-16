#!/bin/bash
set -e
SDK=/home/aligurgi4/toolchain
BT=/home/aligurgi4/Android/Sdk/build-tools/34.0.0
BT36=/home/aligurgi4/Android/Sdk/build-tools/36.0.0
AAPT2=$SDK/aapt2bin/aapt2
JAVA=$SDK/jdk/bin/java
APKSIGNER=$BT/apksigner
ZIPALIGN=$BT36/zipalign
export PATH=$SDK/jdk/bin:$PATH
cd /home/aligurgi4/Projects/cr
rm -rf build gen
mkdir -p build gen

$AAPT2 compile --dir res -o build/res.zip
$AAPT2 link -I $SDK/android.jar --manifest AndroidManifest.xml \
  --java gen --auto-add-overlay -A assets --min-sdk-version 26 \
  --target-sdk-version 34 -o build/app-base.apk build/res.zip
mkdir -p build/classes
javac --release 8 -cp "$SDK/android.jar:/home/aligurgi4/Projects/cr/libs/voskx/classes.jar:/home/aligurgi4/Projects/cr/libs/jna/classes.jar" -d build/classes $(find gen src -name '*.java')
$JAVA -cp $SDK/r8-8.7.18.jar com.android.tools.r8.D8 --min-api 26 --lib $SDK/android.jar \
  --lib /home/aligurgi4/Projects/cr/libs/voskx/classes.jar \
  --lib /home/aligurgi4/Projects/cr/libs/jna/classes.jar \
  --output build $(find build/classes -name '*.class') /home/aligurgi4/Projects/cr/libs/voskx/classes.jar /home/aligurgi4/Projects/cr/libs/jna/classes.jar
cd build
cp app-base.apk app-debug.apk
python3 -c "import zipfile; z=zipfile.ZipFile('app-debug.apk','a',zipfile.ZIP_DEFLATED); z.write('classes.dex'); z.close()"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do mkdir -p lib/$abi; cp /home/aligurgi4/Projects/cr/libs/voskx/jni/$abi/*.so lib/$abi/ 2>/dev/null || cp /home/aligurgi4/Projects/cr/libs/voskx/jni/$abi/*.so lib/$abi/; cp /home/aligurgi4/Projects/cr/libs/jna/jni/$abi/*.so lib/$abi/ 2>/dev/null || true; cp /home/aligurgi4/Projects/cr/libs/crtts/jni/$abi/*.so lib/$abi/ 2>/dev/null || true; done
python3 /home/aligurgi4/Projects/cr/addlibs.py app-debug.apk lib
cd /home/aligurgi4/Projects/cr

# zipalign before signing (required for install on modern Android).
# -P 16 aligns uncompressed .so files to 16 KB pages for Android 15+ devices.
$ZIPALIGN -f -P 16 4 build/app-debug.apk build/app-aligned.apk

if [ ! -f debug.keystore ]; then
  keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
    -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

# Sign with v1 + v2 + v3 (v2 is mandatory for targetSdk >= 30 on Android 11+)
$APKSIGNER sign \
  --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out build/app-signed.apk build/app-aligned.apk

$APKSIGNER verify --verbose build/app-signed.apk
cp build/app-signed.apk /home/aligurgi4/Projects/cr/app-debug.apk
ls -la app-debug.apk

#!/usr/bin/env bash
# Build the SH Bridge LSPosed module APK without Gradle.
#
# Requires:
#   JDK 8+
#   Android build-tools 30.0.3   (aapt2, d8, zipalign, apksigner)
#   Android platform 30          (android.jar)
#
# Environment:
#   ANDROID_BUILD_TOOLS   path to the build-tools directory (required)
#   ANDROID_JAR           path to android.jar (required)
#   KEYSTORE              release keystore, default module/build/shbridge.jks (auto-created)
#   KEYSTORE_ALIAS        default "shbridge"
#   KEYSTORE_PASS         default "shbridge"
#   KEY_PASS              default "shbridge"
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$HERE/build/work"
OUT="$HERE/build/out"

BT="${ANDROID_BUILD_TOOLS:-}"
PLAT="${ANDROID_JAR:-}"
[ -n "$BT" ] || { echo "error: set ANDROID_BUILD_TOOLS to the build-tools 30.0.3 directory" >&2; exit 1; }
[ -n "$PLAT" ] || { echo "error: set ANDROID_JAR to platforms/android-30/android.jar" >&2; exit 1; }
[ -f "$PLAT" ] || { echo "error: android.jar not found: $PLAT" >&2; exit 1; }

tool() {
    if [ -x "$BT/$1" ]; then echo "$BT/$1"; else echo "$BT/$1.exe"; fi
}
AAPT2="$(tool aapt2)"
ZIPALIGN="$(tool zipalign)"
D8_JAR="$BT/lib/d8.jar"
APKSIGNER_JAR="$BT/lib/apksigner.jar"

KEYSTORE="${KEYSTORE:-$HERE/build/shbridge.jks}"
KS_ALIAS="${KEYSTORE_ALIAS:-shbridge}"
KS_PASS="${KEYSTORE_PASS:-shbridge}"
KEY_PASS="${KEY_PASS:-shbridge}"

rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/stubs" "$OUT"

echo "== compile Xposed API stubs"
javac -encoding UTF-8 -nowarn -d "$WORK/stubs" $(find "$HERE/stubs" -name '*.java')

echo "== compile module"
javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "$PLAT" -cp "$WORK/stubs" \
    -d "$WORK/classes" $(find "$HERE/src" -name '*.java')

echo "== dex (d8)"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --min-api 21 --release --lib "$PLAT" \
    --output "$WORK/dex" $(find "$WORK/classes" -name '*.class')

echo "== aapt2 link"
"$AAPT2" compile --dir "$HERE/res" -o "$WORK/res.zip"
"$AAPT2" link -o "$WORK/base.apk" -I "$PLAT" \
    --manifest "$HERE/AndroidManifest.xml" "$WORK/res.zip"

echo "== package (dex + xposed_init)"
python - "$WORK" "$HERE" <<'PY'
import os
import sys
import zipfile

work, here = sys.argv[1], sys.argv[2]
src = os.path.join(work, 'base.apk')
dst = os.path.join(work, 'unsigned.apk')

with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zout.writestr(zi, zin.read(item.filename))
    for path, arc in ((os.path.join(work, 'dex', 'classes.dex'), 'classes.dex'),
                      (os.path.join(here, 'assets', 'xposed_init'), 'assets/xposed_init')):
        zi = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        with open(path, 'rb') as fh:
            zout.writestr(zi, fh.read())
print('packaged', dst)
PY

echo "== sign"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -v -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KEY_PASS" \
        -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=LJLlw, O=LJLlw, C=CN" >/dev/null 2>&1
    echo "created release keystore: $KEYSTORE"
fi
"$ZIPALIGN" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
java -jar "$APKSIGNER_JAR" sign \
    --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$OUT/SH-Bridge.apk" "$WORK/aligned.apk"

java -jar "$APKSIGNER_JAR" verify --print-certs "$OUT/SH-Bridge.apk" | head -2
echo "built: $OUT/SH-Bridge.apk"

#!/bin/bash
# Релизная (ужатая) сборка MiniHome: aapt2 -> javac -> R8 -> zipalign -> apksigner.
# Отличие от build.ps1: вместо d8 работает R8 с правилами r8-rules.pro, поэтому код ужат.
set -e
J="C:/Users/Ja/smart-tv-launcher/toolchain/jdk"
BT="C:/Users/Ja/smart-tv-launcher/toolchain/sdk/build-tools/34.0.0"
AJAR="C:/Users/Ja/smart-tv-launcher/toolchain/sdk/platforms/android-28/android.jar"
KS="C:/Users/Ja/smart-tv-launcher/launcher/litelauncher.keystore"
P="C:/Users/Ja/minilauncher"
OUT="$P/build-release"
export JAVA_HOME="$J"
export PATH="$J/bin:$PATH"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

# Релизный манифест: служебный зонд API (ApiProbeActivity) в релиз не входит.
python - "$P" "$OUT" <<'PY'
import re, sys
p, out = sys.argv[1], sys.argv[2]
src = open(p + "/AndroidManifest.xml", encoding="utf-8").read()
cleaned = re.sub(r'\s*<activity[^>]*ApiProbeActivity[^>]*/>', '', src)
open(out + "/AndroidManifest.release.xml", "w", encoding="utf-8").write(cleaned)
print("манифест для релиза:", "ApiProbeActivity убран" if "ApiProbeActivity" not in cleaned else "НЕ убран")
PY

"$BT/aapt2.exe" compile --dir "$P/res" -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/unsigned.apk" -I "$AJAR" --manifest "$OUT/AndroidManifest.release.xml" \
  -R "$OUT/res.zip" --auto-add-overlay --java "$OUT/gen"

"$J/bin/javac" -encoding UTF-8 --release 8 -nowarn -cp "$AJAR" -d "$OUT/classes" \
  $(find "$P/src" "$OUT/gen" -name '*.java' ! -name 'ApiProbeActivity.java')

( cd "$OUT/classes" && "$J/bin/jar" cf "$OUT/in.jar" . )
echo "классов в jar: $(unzip -l "$OUT/in.jar" | grep -c '\.class')"

"$J/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 21 \
  --lib "$AJAR" --output "$OUT/dex" --pg-conf "$P/r8-rules.pro" "$OUT/in.jar"
ls -l "$OUT/dex"

cp "$OUT/unsigned.apk" "$OUT/with-dex.apk"
( cd "$OUT/dex" && "$J/bin/jar" uf "$OUT/with-dex.apk" classes.dex )

# Максимальное сжатие архива (deflate 9): подпись ставится уже после этой операции.
python - "$OUT" <<'PY'
import sys, zipfile
out = sys.argv[1]
src, dst = out + "/with-dex.apk", out + "/with-dex-max.apk"
zin = zipfile.ZipFile(src)
zout = zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9)
for it in zin.infolist():
    zout.writestr(it, zin.read(it.filename))
zout.close(); zin.close()
print("пересжат до deflate 9")
PY

"$BT/zipalign.exe" -f 4 "$OUT/with-dex-max.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:litelauncher --out "$P/MiniHome-release.apk" "$OUT/aligned.apk"

echo "=== итог:"; ls -l "$P/MiniHome-release.apk" "$P/MiniHome.apk"
unzip -l "$P/MiniHome-release.apk" | tail -8

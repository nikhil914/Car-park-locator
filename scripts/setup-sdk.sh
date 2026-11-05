#!/usr/bin/env bash
# Recreates a minimal Android SDK (platform 35 + build-tools 35.0.0 skeleton)
# WITHOUT dl.google.com. See docs/BUILD.md for rationale.
#
# Usage: scripts/setup-sdk.sh [SDK_DIR]   (default: /home/user/android-sdk)
set -euo pipefail

SDK_DIR="${1:-${ANDROID_HOME:-/home/user/android-sdk}}"
API=35
BT=35.0.0
# Genuine SDK android.jar for API 35 (contains resources.arsc + AndroidManifest.xml,
# so aapt2 link works). Mirrored on GitHub by the Sable project.
ANDROID_JAR_URL="https://raw.githubusercontent.com/Sable/android-platforms/master/android-${API}/android.jar"
ANDROID_JAR_SHA256="4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo ">> SDK dir: $SDK_DIR"
PLAT="$SDK_DIR/platforms/android-$API"
BTD="$SDK_DIR/build-tools/$BT"
mkdir -p "$PLAT" "$BTD" "$SDK_DIR/licenses"

# ---------------------------------------------------------------- platform
if [ ! -f "$PLAT/android.jar" ] || ! echo "$ANDROID_JAR_SHA256  $PLAT/android.jar" | sha256sum -c --status; then
  echo ">> downloading android.jar (API $API)"
  curl -sSfL --retry 3 -o "$WORK/android.jar" "$ANDROID_JAR_URL"
  echo "$ANDROID_JAR_SHA256  $WORK/android.jar" | sha256sum -c -
  mv "$WORK/android.jar" "$PLAT/android.jar"
fi

# core-for-system-modules.jar: AGP's JdkImageTransform (Java 9+ source level)
# runs jlink over it to build the java.base system module. Derive it from the
# java.* / javax.* classes of android.jar (that is what the real one contains).
if [ ! -f "$PLAT/core-for-system-modules.jar" ]; then
  echo ">> generating core-for-system-modules.jar"
  mkdir -p "$WORK/core"
  (cd "$WORK/core" && unzip -q "$PLAT/android.jar" 'java/*' 'javax/*' && zip -qr "$PLAT/core-for-system-modules.jar" java javax)
fi

cat > "$PLAT/source.properties" <<P
Pkg.Desc=Android SDK Platform $API
Pkg.UserSrc=false
Platform.Version=15
Platform.CodeName=
Pkg.Revision=2
AndroidVersion.ApiLevel=$API
Layoutlib.Api=15
Layoutlib.Revision=1
Platform.MinToolsRev=22
P
cat > "$PLAT/build.prop" <<P
ro.build.version.sdk=$API
ro.build.version.codename=REL
ro.build.version.release=15
P
cat > "$PLAT/package.xml" <<P
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns3="http://schemas.android.com/repository/android/common/01" xmlns:ns4="http://schemas.android.com/repository/android/generic/01" xmlns:ns5="http://schemas.android.com/repository/android/generic/02" xmlns:ns6="http://schemas.android.com/sdk/android/repo/addon2/01" xmlns:ns7="http://schemas.android.com/sdk/android/repo/addon2/02" xmlns:ns8="http://schemas.android.com/sdk/android/repo/addon2/03" xmlns:ns9="http://schemas.android.com/sdk/android/repo/repository2/01" xmlns:ns10="http://schemas.android.com/sdk/android/repo/repository2/02" xmlns:ns11="http://schemas.android.com/sdk/android/repo/repository2/03" xmlns:ns12="http://schemas.android.com/sdk/android/repo/sys-img2/03" xmlns:ns13="http://schemas.android.com/sdk/android/repo/sys-img2/02" xmlns:ns14="http://schemas.android.com/sdk/android/repo/sys-img2/01"><license id="android-sdk-license" type="text">placeholder</license><localPackage path="platforms;android-$API" obsolete="false"><type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns11:platformDetailsType"><api-level>$API</api-level><codename></codename><base-extension>true</base-extension><layoutlib api="15"/></type-details><revision><major>2</major></revision><display-name>Android SDK Platform $API</display-name><uses-license ref="android-sdk-license"/></localPackage></ns2:repository>
P

# ------------------------------------------------------------- build-tools
# AGP 8.7 downloads aapt2 itself from maven.google.com (com.android.tools.build:aapt2)
# and runs d8/r8/zipflinger/apksig in-process, so build-tools only has to *exist*
# and look valid. We place placeholder executables for the native tools.
for t in aapt aidl zipalign dexdump split-select bcc_compat llvm-rs-cc; do
  if [ ! -e "$BTD/$t" ]; then
    printf '#!/bin/sh\necho "%s: placeholder from scripts/setup-sdk.sh (real tool not installed)" >&2\nexit 1\n' "$t" > "$BTD/$t"
    chmod +x "$BTD/$t"
  fi
done
mkdir -p "$BTD/lib" "$BTD/renderscript/include" "$BTD/renderscript/clang-include" "$BTD/renderscript/lib"
cat > "$BTD/source.properties" <<P
Pkg.Desc=Android SDK Build-Tools $BT
Pkg.UserSrc=false
Pkg.Revision=$BT
P
cat > "$BTD/package.xml" <<P
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns3="http://schemas.android.com/repository/android/common/01" xmlns:ns4="http://schemas.android.com/repository/android/generic/01" xmlns:ns5="http://schemas.android.com/repository/android/generic/02"><license id="android-sdk-license" type="text">placeholder</license><localPackage path="build-tools;$BT" obsolete="false"><type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:genericDetailsType"/><revision><major>${BT%%.*}</major><minor>0</minor><micro>0</micro></revision><display-name>Android SDK Build-Tools ${BT%%.*}</display-name><uses-license ref="android-sdk-license"/></localPackage></ns2:repository>
P

# ---------------------------------------------------------------- licenses
cat > "$SDK_DIR/licenses/android-sdk-license" <<'P'

8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f8a63b6825ea9c5514f83c2829b004d1fee
P

# ---------------------------------------------------------- local.properties
if [ -d "$PROJECT_DIR/app" ]; then
  echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"
  echo ">> wrote $PROJECT_DIR/local.properties"
fi
echo ">> done. export ANDROID_HOME=$SDK_DIR"

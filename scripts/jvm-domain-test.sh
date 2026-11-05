#!/usr/bin/env bash
# Runs the pure-Kotlin domain unit tests (app/src/test/.../domain) on the JVM
# WITHOUT the Android Gradle Plugin. Use this where Google Maven
# (dl.google.com / maven.google.com) is unreachable. See docs/BUILD.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="${GRADLE:-$(command -v gradle || echo /opt/gradle/bin/gradle)}"
PKG=com/realworldmagic/parkpin
W="${WORK_DIR:-${TMPDIR:-/tmp}/parkpin-jvm-domain}"
rm -rf "$W/src"; mkdir -p "$W/src/main/kotlin/$PKG" "$W/src/test/kotlin/$PKG"
cp -r "$ROOT/app/src/main/java/$PKG/domain" "$W/src/main/kotlin/$PKG/"
cp -r "$ROOT/app/src/test/java/$PKG/domain" "$W/src/test/kotlin/$PKG/"
if grep -rl '^import android\.' "$W/src" >/dev/null; then
  echo "domain/ must not import android.*:"; grep -rl '^import android\.' "$W/src"; exit 1
fi
cat > "$W/settings.gradle.kts" <<'K'
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "parkpin-jvm-domain"
K
cat > "$W/build.gradle.kts" <<'K'
plugins { kotlin("jvm") version "2.0.21" }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
tasks.test { testLogging { events("failed"); showStandardStreams = false } }
K
cd "$W" && "$GRADLE" test --console=plain "$@"
echo "Reports: $W/build/reports/tests/test/index.html"

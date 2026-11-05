# Building ParkPin

Project root: `/home/user/car-park-locator` (`/home/user/ParkPin` is a symlink to it).
Toolchain: JDK 17+ (sandbox has JDK 21), Gradle 8.14.3 via the wrapper (`./gradlew`),
AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26.

## Normal machines and GitHub Actions CI

Google Maven and the SDK repository are reachable there, so nothing special is needed:

```bash
# CI: use actions/setup-java (17) + android-actions/setup-android, then
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` (git-ignored) holds `sdk.dir`; on CI `ANDROID_HOME` is used instead.

## Sandbox status (Claude Code cloud sandbox, 2026-11-05)

**`:app:assembleDebug` and `:app:testDebugUnitTest` cannot run in this sandbox.**
The blocker is the Android Gradle Plugin and the AndroidX/Play Services libraries, not
the SDK:

- `google()` resolves to `https://dl.google.com/dl/android/maven2/...`, and the egress
  proxy returns 403 for `dl.google.com`.
- `https://maven.google.com/<artifact>` only answers with a `301` redirect to
  `dl.google.com`. Only its `web/index.html` page loads, so it is not usable as a repository.
- AGP (`com.android.tools.build:gradle`), `aapt2`, `androidx.*` and
  `com.google.android.gms.*` are published only on Google Maven. Maven Central and the
  Gradle Plugin Portal do not carry them. The Plugin Portal redirects to Central, which
  returns 404.
- Common Google Maven mirrors (Aliyun, Huawei, Tencent, JetBrains cache-redirector, JitPack)
  are all refused by the proxy with CONNECT 403.

Gradle fails with:
`Plugin [id: 'com.android.application', version: '8.7.3'] was not found ... could not resolve plugin artifact`.

To build the APK in the sandbox, `dl.google.com` must be allowed in the environment's
network settings. With it allowed, the normal steps above work. You can then also install a
real SDK with `sdkmanager "platforms;android-35" "build-tools;35.0.0"`.

### What works in the sandbox

1. **Gradle wrapper**: `./gradlew` downloads Gradle 8.14.3 from services.gradle.org, which
   redirects to GitHub release assets. This is reachable.
2. **Android SDK skeleton** at `/home/user/android-sdk`, built by `scripts/setup-sdk.sh`
   without dl.google.com:
   - `platforms/android-35/android.jar`: the genuine SDK jar, mirrored at
     `https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar`
     (sha256 `4566663c...619b`, checked by the script). It contains `resources.arsc` and
     `AndroidManifest.xml`, which `aapt2 link` needs. The Reginer/aosp-android-jar variant
     was rejected because it adds hidden APIs.
   - `platforms/android-35/core-for-system-modules.jar`: generated from the `java/*` and
     `javax/*` classes of android.jar. AGP's JdkImageTransform needs this file for Java 17
     source level.
   - `source.properties`, `package.xml` and `build.prop` are hand-written so the SDK
     loader treats the platform as installed.
   - `build-tools/35.0.0`: only `package.xml` and `source.properties`, plus placeholder
     scripts for `aapt`, `aidl`, `zipalign` and similar tools. These scripts exit 1. AGP 8.7
     gets `aapt2` from Maven and runs D8/R8/zipflinger/apksig in-process, so the native
     tools should not be invoked. The project does not use AIDL or RenderScript.
   - `licenses/android-sdk-license` holds the standard accepted-license hashes.
   - **Caveat: this skeleton could not be checked end to end with AGP**, because AGP
     itself cannot be downloaded here. If AGP rejects it, for example with "build-tools
     corrupted" or a missing `core-lambda-stubs.jar`, install the real components instead.
3. **Pure-Kotlin domain tests on the JVM without AGP**: `scripts/jvm-domain-test.sh`
   copies `app/src/main/.../domain` and `app/src/test/.../domain` into a temporary
   Kotlin/JVM Gradle project. It fails if `domain/` imports `android.*`. Then it runs
   JUnit4 using only Maven Central and the Plugin Portal. Last run: 8 test classes,
   70 tests, 0 failures.

## Reproduce (sandbox)

```bash
cd /home/user/car-park-locator
scripts/setup-sdk.sh                 # -> /home/user/android-sdk, writes local.properties
scripts/jvm-domain-test.sh           # JVM domain unit tests (works offline from Google)
./gradlew :app:assembleDebug         # requires dl.google.com to be reachable
./gradlew :app:testDebugUnitTest     # requires dl.google.com to be reachable
```

Notes:
- Maven Central (`repo1.maven.org`) sometimes returns HTTP 429 here. Gradle retries, and
  rerunning the build usually succeeds.
- No changes were made to `compileSdk`/`targetSdk` (still 35) or to
  `gradle/libs.versions.toml`.

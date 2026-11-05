# MapLibre Native ships consumer ProGuard rules in its AAR; these only silence optional deps.
-dontwarn org.maplibre.android.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

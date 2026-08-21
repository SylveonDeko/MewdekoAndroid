-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mewdeko.mobile.**$$serializer { *; }
-keepclassmembers class dev.mewdeko.mobile.** {
    *** Companion;
    *** INSTANCE;
}

# Tink, pulled in by androidx.security-crypto for the token store, references
# ErrorProne annotations that are compile-time only and absent at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Ktor selects its engine reflectively, and OkHttp carries optional Conscrypt
# and BouncyCastle providers that are not bundled here.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# Tink, pulled in by androidx.security-crypto for the token store, references
# ErrorProne annotations that exist only at compile time.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# OkHttp carries optional TLS providers that are not bundled, and Ktor logs
# through an SLF4J binding this build does not ship.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

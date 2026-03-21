# ── JSch ─────────────────────────────────────────────────────────────────────
# JSch loads algorithm implementations by class name via reflection.
-keep class com.jcraft.jsch.** { *; }

# ── BouncyCastle ──────────────────────────────────────────────────────────────
# Registered as a JCE provider; internal classes loaded by name.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── Room ──────────────────────────────────────────────────────────────────────
# Entities and DAOs are accessed via generated code; keep all annotations.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ── Kotlin coroutines / serialization internal ────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

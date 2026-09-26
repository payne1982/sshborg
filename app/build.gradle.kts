import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sshborg"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sshborg"
        minSdk = 29
        targetSdk = 36
        versionCode = 35
        versionName = "1.18.0"
    }

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) localPropsFile.inputStream().use { localProps.load(it) }

    signingConfigs {
        create("release") {
            val sf = localProps.getProperty("signing.storeFile")
            storeFile     = if (sf != null) file(sf) else null
            storePassword = localProps.getProperty("signing.storePassword")
            keyAlias      = localProps.getProperty("signing.keyAlias")
            keyPassword   = localProps.getProperty("signing.keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["allowBackup"] = "true"
            manifestPlaceholders["appLabel"] = "SSHBorgDebug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["allowBackup"] = "false"
            manifestPlaceholders["appLabel"] = "SSHBorg"
        }
    }

    testOptions {
        unitTests {
            // The SSH layer logs through android.util.Log on a couple of fallback paths, and an
            // android.jar stub throws when called. Returning defaults instead keeps a real code
            // path from dying on a log line it only reaches when something unusual happened.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/*/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsch)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.sora.editor)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

// Unit tests run on the JVM: no device, no emulator. The ones that need a corpus of real files
// or a real server are told where to find them by test.properties, which is not in the repo —
// see test.properties.example. Each entry becomes an environment variable (keyCorpus ->
// SSHBORG_KEY_CORPUS); a test whose settings are missing skips itself. A -P property of the same
// name wins over the file, which is how a CI run passes them in.
tasks.withType<Test>().configureEach {
    fun variableFor(name: String) =
        "SSHBORG_" + name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

    val settings = linkedMapOf<String, String>()
    val file = rootProject.file("test.properties")
    if (file.exists()) {
        // Read as UTF-8, not the ISO-8859-1 a .properties file defaults to: a passphrase may
        // well have an accent or an emoji in it, and that is exactly what one of them tests.
        val loaded = Properties()
        file.reader(Charsets.UTF_8).use { loaded.load(it) }
        loaded.forEach { (key, value) -> settings[key.toString()] = value.toString() }
    }
    settings.keys.toList().forEach { name ->
        (project.findProperty(name) as String?)?.let { settings[name] = it }
    }
    listOf("keyCorpus", "keyCorpusPassphrase", "keyCorpusUtf8Passphrase", "editorCorpus",
           "sshHost", "sshPort", "sshUser", "sshKeyDir", "sshKeyPassphrase").forEach { name ->
        if (name !in settings) (project.findProperty(name) as String?)?.let { settings[name] = it }
    }
    settings.forEach { (name, value) -> environment(variableFor(name), value) }

    testLogging { events("passed", "skipped", "failed") }
}

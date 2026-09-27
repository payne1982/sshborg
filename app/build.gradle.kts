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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // The exported schemas ride along in the test APK's assets, which is where
    // MigrationTestHelper looks for them.
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")

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
    constraints {
        // room-testing needs kotlinx-serialization 1.8.x to read the exported schemas, while
        // androidx.navigation asks for 1.7.3 — and AGP's consistent resolution hands the app's
        // resolved version to the instrumented tests as well, where Room then dies with an
        // AbstractMethodError on GeneratedSerializer that names no version at all.
        //
        // `require` is a floor, not a pin: it raises what is already there to at least this, and
        // any dependency asking for more still wins (checked: with a 1.9.0 request in the graph,
        // everything resolves to 1.9.0). The pin that trapped us was the `strictly` in
        // kotlinx-serialization's own BOM. Delete this whole block once navigation asks for
        // 1.8.1 or later by itself.
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json") {
            version { require("1.8.1") }
        }
    }

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
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.room.testing)
}

// Room writes the schema of every version here, and the files are committed: a migration test
// has nothing to validate against without them, and a schema that changed without a migration
// shows up as a diff instead of as a crash on someone's phone.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Unit tests run on the JVM: no device, no emulator. The ones that need a corpus of real files
// or a real server are told where to find them by test.properties, which is not in the repo —
// see test.properties.example. Each entry becomes an environment variable (keyCorpus ->
// SSHBORG_KEY_CORPUS); a test whose settings are missing skips itself. A -P property of the same
// name wins over the file, which is how a CI run passes them in.
// The bundle we upload is built only after the unit tests have passed. Deliberately not wired to
// assembleRelease: that is the command F-Droid builds with, and a test failing on a machine we
// cannot look at would break a build for reasons that have nothing to do with the code.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}

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

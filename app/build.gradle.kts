import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sshborg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sshborg"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    // Use local patched JAR with ChannelAgentForwarding removed (our app provides it).
    // The original com.github.mwiede:jsch:0.2.19 was keeping its own ChannelAgentForwarding
    // in classes9.dex, which took precedence over our override in classes11.dex.
    implementation(files("libs/jsch-0.2.19-patched.jar"))
    implementation(libs.bouncycastle)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.ui.tooling)
}

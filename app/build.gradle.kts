import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing. A local, stable debug key is the default so successive local builds can be
 * installed with `adb install -r` instead of requiring an uninstall. CI or a local signing file
 * can still override it.
 */
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStorePath = signingValue("storeFile", "SIGNING_STORE_FILE")
val releaseStoreFile = releaseStorePath?.let(rootProject::file)
    ?: rootProject.file(".android-user/debug.keystore")

android {
    namespace = "com.os4.musiccover"
    compileSdk = 37

    defaultConfig {
        // This fork has its own module identity. The Java package and probe broadcast actions use
        // the same namespace, so it can be installed and scoped as one independent module.
        applicationId = "com.os4.musiccover"
        minSdk = 35
        targetSdk = 37
        versionCode = (findProperty("mcVersionCode") as String?)?.toInt() ?: 5
        versionName = ((findProperty("mcVersionName") as String?) ?: "0.0.4") +
                ((findProperty("mcVersionSuffix") as String?) ?: "")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD") ?: "android"
            keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            // The settings UI drags in Compose and material-icons-extended, which is tens of
            // megabytes of generated icon code that this app uses a handful of. Without R8 the
            // APK is ~47MB; the module's own code is a rounding error either way.
            // proguard-rules.pro keeps the hook classes, which nothing on the classpath calls.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Pinned, not left to the platform default. NcmLyrics' health check compares against a
    // song title written as characters, and a javac that read this file as the system codepage
    // would hand it a mangled one - the search would then never find the id it looks for, the
    // endpoint would look permanently dishonest, and no miss would ever be cached again. It
    // builds correctly here only because this toolchain's javac already defaults to UTF-8.
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The module and the app both log a full account of the preview pipeline in debug
        // builds and stay quiet in release ones, which needs BuildConfig.DEBUG to exist.
        buildConfig = true
    }
}

dependencies {
    // Modern Xposed API. compileOnly on purpose: the framework provides it at runtime and
    // packaging it would shadow the real one. Zero bytes in the APK either way.
    compileOnly("io.github.libxposed:api:102.0.0")

    // The lyric parser. Its classes end up in the same dex as Main.java's, so they are also
    // loaded into SystemUI when the module is - see LyricProbe, which is why it has to stay
    // dependency-light and Android-free.
    implementation(libs.lyrics.core)
    // The lyric bridge the LyricProvider plugins publish through. Optional at runtime: when
    // nothing on the device implements it, LyriconSource simply never connects.
    implementation(libs.lyricon.subscriber)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.material.icons.extended)

    testImplementation("junit:junit:4.13.2")
}

// The bundled offline cache contains the current Android/Kotlin artifacts. Keep the transitive
// serialization and startup components on those cached versions so local builds do not depend on
// a repository connection just to resolve an older metadata request.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        when (requested.group to requested.name) {
            "org.jetbrains.kotlinx" to "kotlinx-serialization-core",
            "org.jetbrains.kotlinx" to "kotlinx-serialization-json" -> useVersion("1.11.0")
            "androidx.startup" to "startup-runtime" -> useVersion("1.2.0")
        }
    }
}

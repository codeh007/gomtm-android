val fallbackVersionName = "0.4.4-dev"
val releaseTagPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].+)?$""")

fun normalizeVersionName(raw: String?): String {
    val candidate = raw?.trim().orEmpty()
    if (candidate.isBlank()) {
        return fallbackVersionName
    }
    val match = releaseTagPattern.matchEntire(candidate)
        ?: error("gomtm release tag must match vX.Y.Z or X.Y.Z, got: $candidate")
    val (major, minor, patch) = match.destructured
    return "$major.$minor.$patch"
}

fun versionCodeFrom(versionName: String): Int {
    val match = releaseTagPattern.matchEntire(versionName)
        ?: error("versionName must contain semver core, got: $versionName")
    val (major, minor, patch) = match.destructured
    return major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

val appVersionName = normalizeVersionName(
    providers.gradleProperty("gomtmReleaseTag").orNull
        ?: providers.environmentVariable("GOMTM_RELEASE_TAG").orNull,
)
val appVersionCode = versionCodeFrom(appVersionName)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.gomtm.swarm"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gomtm.swarm"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    // 当前公开分发的 app 是最小 Android node shell，优先压缩 native libs，且只产出主流 arm64 包，避免把四个 ABI 的 libgojni.so 全塞进同一个下载 APK。
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(files("libs/gomtm-swarm-android.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

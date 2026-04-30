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
val gomtmDevicesUrl = "https://gomtmui-dev.yuepa8.com/dash/devices"
val localPinnedRuntimeAar = project.file("libs/gomtm-swarm-android.aar")
val worktreePinnedRuntimeAar = rootDir.resolve("../../app/libs/gomtm-swarm-android.aar")
val pinnedRuntimeAar = when {
    localPinnedRuntimeAar.exists() -> localPinnedRuntimeAar
    worktreePinnedRuntimeAar.exists() -> worktreePinnedRuntimeAar
    else -> error("missing gomtm-swarm-android.aar in app/libs or shared worktree fallback")
}
val syncPythonAndroidRuntimeScript = project.file("scripts/sync_python_android_runtime.py")
val generatedBundledPythonRuntimeDir = layout.buildDirectory.dir("generated/pythonRuntime/bundled")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val syncPythonAndroidRuntime = tasks.register<Exec>("syncPythonAndroidRuntime") {
    group = "build setup"
    description = "Vendors the official Python Android runtime into the app build tree"

    inputs.file(syncPythonAndroidRuntimeScript)
    outputs.dir(generatedBundledPythonRuntimeDir)

    commandLine(
        "python3",
        syncPythonAndroidRuntimeScript.absolutePath,
        "--output-root",
        generatedBundledPythonRuntimeDir.get().asFile.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(syncPythonAndroidRuntime)
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
        buildConfigField("String", "GOMTM_UI_DEVICES_URL", "\"$gomtmDevicesUrl\"")

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

	externalNativeBuild {
		cmake {
			path = file("src/main/c/CMakeLists.txt")
		}
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
    implementation(files(pinnedRuntimeAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

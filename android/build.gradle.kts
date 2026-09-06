plugins {
    id("com.android.application")
}

fun commandOutput(vararg command: String): String? = try {
    val process = ProcessBuilder(*command)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) output else null
} catch (_: Exception) {
    null
}

val revisionPattern = Regex("[A-Za-z0-9._+\\-]{1,64}")
val revisionOverride = System.getenv("ROBOWINDOWS_BUILD_REVISION")
val discoveredRevision = revisionOverride ?: commandOutput("git", "rev-parse", "--short=12", "HEAD")
val safeRevision = discoveredRevision?.takeIf { revisionPattern.matches(it) } ?: "unknown"
val sourceRevision = if (revisionOverride == null && safeRevision != "unknown" &&
    !commandOutput("git", "status", "--porcelain", "--untracked-files=normal").isNullOrEmpty()
) "$safeRevision+dirty" else safeRevision

android {
    namespace = "org.robowindows.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.robowindows.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            ndkBuild {
                arguments += "APP_PLATFORM=android-26"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

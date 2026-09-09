import java.security.MessageDigest

plugins {
    id("com.android.application")
}

fun contentRevision(paths: List<String>, extra: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    paths.sorted().forEach { path ->
        digest.update(path.toByteArray(Charsets.UTF_8))
        digest.update(rootProject.file(path).readBytes())
    }
    extra.forEach { digest.update(it.toByteArray(Charsets.UTF_8)) }
    return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
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
val cpuDiagnosticRevision = contentRevision(listOf(
    "tests/cpu/robowindows_cpu_boot.S",
    "tests/cpu/robowindows_cpu_fixture.S",
    "scripts/build-cpu-fixture.sh",
    "tests/cpu/robowindows_x86_gate_boot.S",
    "tests/cpu/robowindows_x86_gate.S",
    "scripts/build-expanded-cpu-fixtures.sh",
    "android/src/main/java/org/robowindows/app/CpuFixtureFiles.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureGate.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureProtocol.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureResult.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureService.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureClient.java",
    "android/src/main/java/org/robowindows/app/CpuFixtureController.java",
    "android/src/main/java/org/robowindows/app/ExpandedCpuSuite.java",
    "android/src/main/java/org/robowindows/app/ExpandedCpuResult.java",
    "android/src/main/java/org/robowindows/app/ExpandedCpuReport.java",
    "android/src/main/java/org/robowindows/app/NormalCpuFixtureService.java",
    "android/src/main/java/org/robowindows/app/DynamicCpuFixtureService.java",
    "android/src/main/cpp/core_host.cpp",
    "android/src/main/cpp/Android.mk",
    "patches/dosbox-pure/0001-preserve-conf-on-guest-reboot.patch",
    "patches/dosbox-pure/0002-dynrec-consume-invlpg-address.patch",
    "patches/dosbox-pure/0003-dynrec-stosd-precise-page-fault.patch",
    "patches/dosbox-pure/0004-dynrec-precise-string-page-faults.patch",
    "patches/dosbox-pure/0005-dynrec-honor-supervisor-write-protect.patch"
), listOf("dosbox-pure:7f6e8fb7385fa446d1444d671063268520bf9b54"))

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
        buildConfigField("String", "CPU_DIAGNOSTIC_REVISION", "\"$cpuDiagnosticRevision\"")

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

    sourceSets["main"].res.srcDir("build/generated/cpuFixture/res")

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

val cpuFixtureOutput = layout.buildDirectory.file(
    "generated/cpuFixture/res/raw/robowindows_cpu_v3.bin")
val buildCpuFixture by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("tests/cpu/robowindows_cpu_boot.S"))
    inputs.file(rootProject.file("tests/cpu/robowindows_cpu_fixture.S"))
    inputs.file(rootProject.file("scripts/build-cpu-fixture.sh"))
    outputs.file(cpuFixtureOutput)
    commandLine(rootProject.file("scripts/build-cpu-fixture.sh"),
        cpuFixtureOutput.get().asFile)
}

val expandedCpuFixtureDir = layout.buildDirectory.dir("generated/cpuFixture/res/raw")
val buildExpandedCpuFixtures by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("tests/cpu/robowindows_x86_gate_boot.S"))
    inputs.file(rootProject.file("tests/cpu/robowindows_x86_gate.S"))
    inputs.file(rootProject.file("scripts/build-expanded-cpu-fixtures.sh"))
    outputs.files(ExpandedCpuSuiteResourceNames.outputs(expandedCpuFixtureDir))
    commandLine(rootProject.file("scripts/build-expanded-cpu-fixtures.sh"),
        expandedCpuFixtureDir.get().asFile)
}

tasks.named("preBuild").configure {
    dependsOn(buildCpuFixture)
    dependsOn(buildExpandedCpuFixtures)
}

object ExpandedCpuSuiteResourceNames {
    fun outputs(directory: Provider<Directory>) = listOf(
        "strings", "fault_retry", "pagefault_progress", "integer_flags", "stack_control",
        "paging_smc", "x87", "mixed_seeds"
    ).map { directory.map { dir -> dir.file("robowindows_x86_gate_$it.bin") } }
}

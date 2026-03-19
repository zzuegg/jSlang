import java.net.URI

plugins {
    java
}

val slangVersion: String by project

fun slangPlatform(): String {
    val os = System.getProperty("os.name", "").lowercase()
    val arch = System.getProperty("os.arch", "").lowercase()
    val osName = when {
        "linux" in os -> "linux"
        "mac" in os || "darwin" in os -> "macos"
        "win" in os -> "windows"
        else -> error("Unsupported OS: $os")
    }
    val archName = when (arch) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> error("Unsupported arch: $arch")
    }
    return "$osName-$archName"
}

val slangNativesDir = layout.buildDirectory.dir("slang-natives")

val downloadSlang by tasks.registering {
    val outputDir = slangNativesDir.get().asFile
    val marker = File(outputDir, ".slang-$slangVersion")
    outputs.dir(outputDir)
    onlyIf { !marker.exists() }
    doLast {
        val platform = slangPlatform()
        val archiveName = "slang-$slangVersion-$platform"
        val url = "https://github.com/shader-slang/slang/releases/download/v$slangVersion/$archiveName.zip"
        val zipFile = File(outputDir, "$archiveName.zip")
        outputDir.mkdirs()
        logger.lifecycle("Downloading Slang $slangVersion for $platform...")
        val uri = URI.create(url)
        uri.toURL().openStream().use { input: java.io.InputStream ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Extracting...")
        copy {
            from(zipTree(zipFile))
            into(outputDir)
        }
        zipFile.delete()
        // Zip doesn't preserve symlinks — fix up stub files that should point to versioned .so files
        val libDir = File(outputDir, "lib")
        if (libDir.exists()) {
            libDir.listFiles()?.filter { it.length() < 200 && it.name.endsWith(".so") }?.forEach { stub ->
                val target = stub.readText().trim()
                val targetFile = File(libDir, target)
                if (targetFile.exists() && targetFile != stub) {
                    stub.delete()
                    targetFile.copyTo(stub)
                    logger.lifecycle("  Fixed symlink: ${stub.name} -> $target")
                }
            }
        }
        marker.createNewFile()
    }
}

val slangLibPath: String
    get() {
        val platform = slangPlatform()
        val base = slangNativesDir.get().asFile
        val libDir = File(base, "slang-$slangVersion-$platform/lib")
        if (libDir.exists()) return libDir.absolutePath
        val fallback = File(base, "lib")
        if (fallback.exists()) return fallback.absolutePath
        return libDir.absolutePath
    }

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(property("javaVersion").toString().toInt()))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("--enable-preview"))
    }

    tasks.withType<Test> {
        dependsOn(downloadSlang)
        useJUnitPlatform()
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=$slangLibPath")
        val envPath = System.getenv("LD_LIBRARY_PATH") ?: ""
        val fullPath = if (envPath.isNotEmpty()) "$slangLibPath:$envPath" else slangLibPath
        environment("LD_LIBRARY_PATH", fullPath)
        // Fork per class to isolate native library lifecycle
        forkEvery = 1
    }

    tasks.withType<JavaExec> {
        dependsOn(downloadSlang)
        jvmArgs("-Djava.library.path=$slangLibPath")
        val envPath = System.getenv("LD_LIBRARY_PATH") ?: ""
        val fullPath = if (envPath.isNotEmpty()) "$slangLibPath:$envPath" else slangLibPath
        environment("LD_LIBRARY_PATH", fullPath)
    }
}

plugins {
    java
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
        useJUnitPlatform()
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
        // Inherit LD_LIBRARY_PATH so tests can find libslang.so
        environment("LD_LIBRARY_PATH", System.getenv("LD_LIBRARY_PATH") ?: "")
        // Fork per class to isolate native library lifecycle
        forkEvery = 1
    }
}

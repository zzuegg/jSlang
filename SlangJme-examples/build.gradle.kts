repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":SlangJme"))
    implementation(project(":api"))
    implementation(project(":bindings"))
    implementation("org.jmonkeyengine:jme3-core:3.10.0-local")
    implementation("org.jmonkeyengine:jme3-desktop:3.10.0-local")
    implementation("org.jmonkeyengine:jme3-lwjgl3:3.10.0-local")
}

tasks.register<JavaExec>("runPbrDemo") {
    mainClass.set("dev.slang.jme.examples.PbrDemo")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("runMultiMaterialDemo") {
    mainClass.set("dev.slang.jme.examples.MultiMaterialDemo")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}

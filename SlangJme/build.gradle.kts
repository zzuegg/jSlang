repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":api"))
    implementation(project(":bindings"))
    compileOnly("org.jmonkeyengine:jme3-core:3.10.0-local")

    testImplementation("org.jmonkeyengine:jme3-core:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-desktop:3.10.0-local")
    testImplementation("org.jmonkeyengine:jme3-lwjgl3:3.10.0-local")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

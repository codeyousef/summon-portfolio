import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.1.0"
}

group = "codes.yousef.seen"
version = "0.1.0-dev"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

application {
    mainClass.set("codes.yousef.seen.registry.ApplicationKt")
}

dependencies {
    implementation("codes.yousef.aether:aether-core-jvm:0.6.0.0")
    implementation("codes.yousef.aether:aether-web-jvm:0.6.0.0")
    implementation("codes.yousef:summon:0.7.0.3")
    implementation("io.vertx:vertx-core:4.5.11")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    implementation(platform("com.google.cloud:libraries-bom:26.51.0"))
    implementation("com.google.cloud:google-cloud-firestore")
    implementation("com.google.cloud:google-cloud-storage")
    implementation("com.google.cloud:google-cloud-kms")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ShadowJar>().configureEach {
    mergeServiceFiles()
    archiveClassifier.set("all")
    isZip64 = true
}

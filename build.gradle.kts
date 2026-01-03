val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.3.1"
    id("com.gradleup.shadow") version "9.1.0"
}

group = "code.yousef"
version = "0.0.2"

application {
    mainClass.set("code.yousef.ApplicationKt")
}

dependencies {
    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-caching-headers")
    implementation("io.ktor:ktor-server-auto-head-response")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-host-common")

    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-encoding")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("org.commonmark:commonmark-ext-yaml-front-matter:0.22.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")
    implementation("org.jsoup:jsoup:1.18.1")

    // Summon SSR framework + Sigil 3D/effects library
    implementation("codes.yousef:summon:0.6.2.1")
    // Materia 0.3.4.6 - Fixed WebGL uniform name mismatch
    implementation("codes.yousef:materia-jvm:0.3.4.6")
    // Sigil 0.2.9.1 - Shared RenderLoop and UniformUpdater utilities
    implementation("codes.yousef.sigil:sigil-schema-jvm:0.2.9.1")
    implementation("codes.yousef.sigil:sigil-summon-jvm:0.2.9.1")

    // Aether Framework 0.3.6.0 - Django-like KMP framework with admin, auth, forms
    // 0.3.6.0 - Fix body reading: set up handlers synchronously before coroutine
    implementation("codes.yousef.aether:aether-core-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-web-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-ui-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-db-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-auth-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-forms-jvm:0.3.6.0")
    implementation("codes.yousef.aether:aether-admin-jvm:0.3.6.0")

    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Cloud Firestore (ready for future integrations)
    implementation(platform("com.google.cloud:libraries-bom:26.51.0"))
    implementation("com.google.cloud:google-cloud-firestore")

    // Apache POI for Excel (.xlsx) import
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks {
    shadowJar {
        mergeServiceFiles()
        isZip64 = true
    }
}

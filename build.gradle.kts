plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
}

group = "code.yousef"
version = "0.0.2"

application {
    mainClass.set("code.yousef.ApplicationKt")
}

dependencies {
    // Ktor server
    implementation(libs.bundles.ktor.server)

    // Ktor client
    implementation(libs.bundles.ktor.client)

    // Kotlin extensions
    implementation(libs.kotlinx.datetime)

    // Caching
    implementation(libs.caffeine)

    // Markdown & HTML
    implementation(libs.bundles.commonmark)
    implementation(libs.owasp.html.sanitizer)
    implementation(libs.jsoup)

    // Summon SSR framework
    implementation(libs.summon)

    // Materia engine
    implementation(libs.materia.jvm)

    // Sigil 3D/effects
    implementation(libs.bundles.sigil)

    // Aether framework
    implementation(libs.bundles.aether)

    // Logging
    implementation(libs.logback.classic)

    // Google Cloud Firestore
    implementation(platform(libs.google.cloud.bom))
    implementation(libs.google.cloud.firestore)

    // Apache POI for Excel (.xlsx) import
    implementation(libs.poi.ooxml)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks {
    shadowJar {
        mergeServiceFiles()
        isZip64 = true
    }
}

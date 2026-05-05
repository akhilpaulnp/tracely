plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.21"
}

dependencies {
    // HTTP client for trace_processor_shell
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")

    // Protobuf for Perfetto wire format
    implementation("com.google.protobuf:protobuf-kotlin:4.31.1")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Testing
    testImplementation(kotlin("test"))
}

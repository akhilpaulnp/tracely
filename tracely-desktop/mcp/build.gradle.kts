plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("dev.tracely.mcp.McpMainKt")
}

dependencies {
    implementation(project(":core"))

    // MCP Kotlin SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Testing
    testImplementation(kotlin("test"))
}

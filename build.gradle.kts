plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    // detekt 2.0.0-alpha.3: first detekt line that supports Kotlin 2.x. The
    // 1.23.x series is locked to Kotlin <= 2.0 and fails the build with a
    // "compiled with Kotlin 2.0.21, running with 2.3.21" message regardless of
    // kotlin-compiler-embeddable override. Plugin coordinates moved from
    // io.gitlab.arturbosch.detekt → dev.detekt; task classes from
    // io.gitlab.arturbosch.detekt.* → dev.detekt.gradle.*.
    id("dev.detekt") version "2.0.0-alpha.3"
}

group = "com.openCasino"
version = "1.1.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.3.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux:4.0.6")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:4.0.6")
    implementation("org.postgresql:r2dbc-postgresql:1.1.1.RELEASE")
    implementation("org.liquibase:liquibase-core:5.0.3")
    implementation("org.springframework.boot:spring-boot-liquibase:4.0.6")
    implementation("org.springframework:spring-jdbc:7.0.7")
    runtimeOnly("org.postgresql:postgresql:42.7.11")
    // implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("io.projectreactor:reactor-test:3.8.5")
    developmentOnly("org.springframework.boot:spring-boot-devtools:4.0.6")
    // runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.6")
    testImplementation("org.springframework.boot:spring-boot-webtestclient:4.0.6")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.3.21")
    runtimeOnly("io.r2dbc:r2dbc-h2:1.1.0.RELEASE")
    testRuntimeOnly("com.h2database:h2:2.4.240")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.6")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:4.0.6")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client:4.0.6")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    testImplementation("org.springframework.security:spring-security-test:7.0.5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// MR-10: lint setup.
//
// Strategy: enable the default rule set of both linters across `src/`, but
// freeze every existing violation into a baseline so the first MR introduces
// zero auto-fix churn (which would otherwise destroy `git blame`). New code is
// held to the full rule set; existing code is grandfathered until it is
// touched. Baselines are generated locally via:
//   ./gradlew ktlintCheck --baseline=ktlint-baseline.xml   (writes baseline)
//   ./gradlew detektBaseline                               (writes baseline)
ktlint {
    version.set("1.6.0")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    baseline.set(file("config/ktlint/baseline.xml"))
    filter {
        exclude { it.file.path.contains("/build/") }
        exclude { it.file.path.contains("/generated/") }
    }
}

detekt {
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    autoCorrect = false
    ignoreFailures = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("21")
    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("21")
}

// MR-10: WebSocket message code catalog codegen.
//
// MessageTypes.kt is the source of truth for the numeric protocol codes used
// in the WebSocket envelope. The TypeScript counterpart lives in the
// opencasino-docs repo at api/types.ts (so the frontend can copy it verbatim).
// Historically the two had to be kept in sync by hand and drifted (e.g. AUTH_EVENT
// / GAME_LIST_UPDATE collision comment in types.ts which no longer matches code).
//
// This task parses top-level `const val NAME = NUMBER` declarations and emits a
// TS block ready to splice between BEGIN/END GENERATED markers in api/types.ts.
// The CI job in .gitlab-ci.yml takes the output and commits it back to docs.
tasks.register("generateMessageTypesTs") {
    group = "codegen"
    description = "Regenerate the MessageType enum TS block from MessageTypes.kt."
    val input =
        layout.projectDirectory.file(
            "src/main/kotlin/com/opencasino/server/config/MessageTypes.kt",
        )
    val output = layout.buildDirectory.file("generated/api/messageTypes.generated.ts")
    inputs.file(input)
    outputs.file(output)
    doLast {
        val constLine = Regex("""^const val (\w+) = (\d+)\s*$""")
        val entries =
            input.asFile
                .readLines()
                .mapNotNull { line -> constLine.matchEntire(line.trim())?.destructured?.let { (n, v) -> n to v.toInt() } }
        check(entries.isNotEmpty()) { "No `const val NAME = NUMBER` lines found in ${input.asFile}" }
        val sorted = entries.sortedBy { it.second }
        val out = output.get().asFile
        out.parentFile.mkdirs()
        out.writeText(
            buildString {
                appendLine("// AUTO-GENERATED by `./gradlew generateMessageTypesTs`.")
                appendLine("// Source: src/main/kotlin/com/opencasino/server/config/MessageTypes.kt")
                appendLine("// DO NOT EDIT BY HAND — your changes will be overwritten by CI on next push.")
                appendLine("export const MessageType = {")
                sorted.forEach { (name, value) -> appendLine("  $name: $value,") }
                appendLine("} as const;")
                appendLine("export type MessageTypeCode = typeof MessageType[keyof typeof MessageType];")
            },
        )
        logger.lifecycle("Wrote ${sorted.size} message codes to ${out.relativeTo(rootDir)}")
    }
}

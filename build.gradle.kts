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
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4 / reactor-netty 4.2 pulls HTTP/3 QUIC native libs for five
// OS/arch combos (~12 MB total). The server speaks WebSocket over TCP and has
// no QUIC code path, so the native binaries are dead weight in the fat jar.
// The companion `netty-codec-classes-quic` jar must stay — reactor-netty
// references io.netty.handler.codec.quic.Quic on the bootstrap path and
// excluding it causes NoClassDefFoundError at runtime under load.
configurations.all {
    exclude(group = "io.netty", module = "netty-codec-native-quic")
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.apache.commons:commons-lang3:3.20.0")
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
    testRuntimeOnly("io.r2dbc:r2dbc-h2:1.1.0.RELEASE")
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
    // Capture inputs/outputs/rootDir as plain values so the doLast action does
    // not close over the Gradle Project (which is unserializable and breaks
    // the configuration cache).
    val inputFile =
        layout.projectDirectory
            .file("src/main/kotlin/com/opencasino/server/config/MessageTypes.kt")
            .asFile
    val outputFile =
        layout.buildDirectory
            .file("generated/api/messageTypes.generated.ts")
            .get()
            .asFile
    val projectRoot = rootDir
    inputs.file(inputFile)
    outputs.file(outputFile)
    doLast {
        val constLine = Regex("""^const val (\w+) = (\d+)\s*$""")
        val entries =
            inputFile
                .readLines()
                .mapNotNull { line -> constLine.matchEntire(line.trim())?.destructured?.let { (n, v) -> n to v.toInt() } }
        check(entries.isNotEmpty()) { "No `const val NAME = NUMBER` lines found in $inputFile" }
        val sorted = entries.sortedBy { it.second }
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
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
        logger.lifecycle("Wrote ${sorted.size} message codes to ${outputFile.relativeTo(projectRoot)}")
    }
}

// §2.3: payload data-class codegen.
//
// Sibling to generateMessageTypesTs above, but for the `*Pack` / `*Event` payload
// data classes (network/pack/**, event/**). These used to be hand-synced with
// opencasino-docs/api/types.ts and drifted (e.g. MenuUpdatePack.games/pokerRooms
// added on the server but missing in types.ts for a long time).
//
// Which classes are wire-relevant — and what their TS interface is named — is an
// explicit allow-list in config/codegen/ts-export-manifest.txt, NOT a directory
// scan: network/pack/** has duplicate simple names across packages and dead
// legacy classes, and types.ts renames the wire types (Blackjack*/Poker*).
//
// The generator is intentionally a header parser (regex over the primary ctor),
// not a full Kotlin frontend: the payload classes are flat (only List/Collection/
// Set, nullable, and references to other types/enums). Per-field narrowing that
// TS wants but Kotlin widens (String -> union) is carried by a trailing
// `// ts: <TsType>` hint on the property; field/class KDoc and `@Deprecated` are
// carried through too, so Kotlin is the single source of truth.
tasks.register("generatePackInterfacesTs") {
    group = "codegen"
    description = "Regenerate the payload-interface TS block from the *Pack/*Event data classes."
    val manifestFile =
        layout.projectDirectory
            .file("config/codegen/ts-export-manifest.txt")
            .asFile
    val srcRoot =
        layout.projectDirectory
            .dir("src/main/kotlin")
            .asFile
    val outputFile =
        layout.buildDirectory
            .file("generated/api/packInterfaces.generated.ts")
            .get()
            .asFile
    val projectRoot = rootDir
    inputs.file(manifestFile)
    inputs.dir(srcRoot)
    outputs.file(outputFile)
    doLast {
        // --- parse manifest -------------------------------------------------
        val interfaces = mutableListOf<Pair<String, String>>()
        val typeMap = mutableMapOf<String, String>()
        var section = ""
        manifestFile.readLines().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.trim('[', ']')
                return@forEach
            }
            val eq = line.indexOf('=')
            check(eq > 0) { "Malformed manifest line (expected `key = value`): $raw" }
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when (section) {
                "interfaces" -> interfaces += key to value
                "typemap" -> typeMap[key] = value
                else -> error("Manifest entry outside a [section]: $raw")
            }
        }
        check(interfaces.isNotEmpty()) { "No [interfaces] entries in $manifestFile" }

        // --- TS type mapping ------------------------------------------------
        val primitives =
            mapOf(
                "String" to "string",
                "Int" to "number",
                "Long" to "number",
                "Short" to "number",
                "Byte" to "number",
                "Float" to "number",
                "Double" to "number",
                "Boolean" to "boolean",
                "Any" to "unknown",
            )

        fun mapType(input: String): String {
            var t = input.trim()
            var nullable = false
            if (t.endsWith("?")) {
                nullable = true
                t = t.dropLast(1).trim()
            }
            val list = Regex("""^(?:List|Collection|Set|MutableList|MutableSet)<(.+)>$""").matchEntire(t)
            val base =
                if (list != null) {
                    val inner = mapType(list.groupValues[1])
                    if (inner.contains(" ")) "($inner)[]" else "$inner[]"
                } else {
                    primitives[t] ?: typeMap[t] ?: t
                }
            // `unknown` already subsumes null/undefined — no `| null` noise.
            return if (nullable && base != "unknown") "$base | null" else base
        }

        // --- parse one data class's primary-constructor properties ----------
        fun docComment(
            lines: List<String>,
            indent: String,
        ): List<String> {
            if (lines.isEmpty()) return emptyList()
            val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
            if (clean.isEmpty()) return emptyList()
            if (clean.size == 1) return listOf("$indent/** ${clean[0]} */")
            return buildList {
                add("$indent/**")
                clean.forEach { add("$indent * $it") }
                add("$indent */")
            }
        }

        // Strip Kotlin comment markers from an accumulated leading-comment block.
        fun stripDoc(rawDoc: List<String>): List<String> =
            rawDoc.flatMap { line ->
                line
                    .trim()
                    .removePrefix("/**")
                    .removePrefix("/*")
                    .removeSuffix("*/")
                    .let { if (it.trimStart().startsWith("*")) it.trimStart().removePrefix("*") else it }
                    .removePrefix("//")
                    .trim()
                    .let { if (it.isEmpty()) emptyList() else listOf(it) }
            }

        data class TsField(
            val name: String,
            val type: String,
            val optional: Boolean,
            val doc: List<String>,
        )

        fun parseClass(
            text: String,
            simple: String,
            fqcn: String,
        ): Pair<List<String>, List<TsField>> {
            val header =
                Regex("""(?m)^[^\n]*\bclass\s+$simple\b[^(\n]*\(""").find(text)
                    ?: error("class $simple not found for $fqcn")
            // Capture leading KDoc/comment block directly above the class header.
            val before = text.substring(0, header.range.first).trimEnd('\n')
            val classDoc = mutableListOf<String>()
            if (before.endsWith("*/")) {
                val open = before.lastIndexOf("/**").let { if (it < 0) before.lastIndexOf("/*") else it }
                if (open >= 0) classDoc += before.substring(open).lines()
            }
            // Balance parens of the primary constructor.
            val open = text.indexOf('(', header.range.last - 1)
            var depth = 0
            var i = open
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            val body = text.substring(open + 1, i)

            val fields = mutableListOf<TsField>()
            val pendingDoc = mutableListOf<String>()
            var pendingDeprecated: String? = null
            var inKdoc = false
            body.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                if (inKdoc) {
                    pendingDoc += line
                    if (line.contains("*/")) inKdoc = false
                    return@forEach
                }
                if (line.startsWith("/**") || line.startsWith("/*")) {
                    pendingDoc += line
                    if (!line.contains("*/")) inKdoc = true
                    return@forEach
                }
                if (line.startsWith("//")) {
                    pendingDoc += line
                    return@forEach
                }
                if (line.startsWith("@Deprecated")) {
                    pendingDeprecated =
                        Regex(""""(.*?)"""").find(line)?.groupValues?.get(1) ?: "deprecated"
                    return@forEach
                }
                if (line.startsWith("val ") || line.startsWith("var ")) {
                    var work = line
                    val override =
                        Regex("""//\s*ts:\s*(.+)$""")
                            .find(work)
                            ?.groupValues
                            ?.get(1)
                            ?.trim()
                    work =
                        work
                            .substringBefore("//")
                            .trim()
                            .trimEnd(',')
                            .trim()
                    val m =
                        Regex("""^(?:val|var)\s+(\w+)\s*:\s*(.+)$""").matchEntire(work)
                            ?: error("Cannot parse property in $fqcn: $line")
                    val name = m.groupValues[1]
                    var rest = m.groupValues[2].trim()
                    val hasDefault = rest.contains("=")
                    val ktType = (if (hasDefault) rest.substringBefore("=") else rest).trim()
                    val tsType = override ?: mapType(ktType)
                    val doc = stripDoc(pendingDoc).toMutableList()
                    pendingDeprecated?.let { doc.add(0, "@deprecated $it") }
                    fields += TsField(name, tsType, hasDefault, doc)
                    pendingDoc.clear()
                    pendingDeprecated = null
                    return@forEach
                }
                // anything else (e.g. supertype tail `) : Pack`) — ignore
            }
            return stripDoc(classDoc) to fields
        }

        // --- resolve each manifest entry to a source file -------------------
        val out = StringBuilder()
        out.appendLine("// AUTO-GENERATED by `./gradlew generatePackInterfacesTs`.")
        out.appendLine("// Source: Kotlin payload data classes, see config/codegen/ts-export-manifest.txt")
        out.appendLine("// DO NOT EDIT BY HAND — your changes will be overwritten by CI on next push.")
        var emitted = 0
        interfaces.forEach { (fqcn, tsName) ->
            val pkg = fqcn.substringBeforeLast('.')
            val simple = fqcn.substringAfterLast('.')
            val dir = File(srcRoot, pkg.replace('.', '/'))
            check(dir.isDirectory) { "Package dir not found for $fqcn: $dir" }
            val file =
                dir
                    .listFiles { f -> f.extension == "kt" }
                    ?.firstOrNull { Regex("""(?m)^[^\n]*\bclass\s+$simple\b""").containsMatchIn(it.readText()) }
                    ?: error("No .kt in $dir declares class $simple ($fqcn)")
            val (classDoc, fields) = parseClass(file.readText(), simple, fqcn)
            check(fields.isNotEmpty()) { "Class $fqcn has no constructor properties to emit" }
            out.appendLine()
            docComment(classDoc, "").forEach { out.appendLine(it) }
            out.appendLine("export interface $tsName {")
            fields.forEach { f ->
                docComment(f.doc, "  ").forEach { out.appendLine(it) }
                out.appendLine("  ${f.name}${if (f.optional) "?" else ""}: ${f.type};")
            }
            out.appendLine("}")
            emitted++
        }
        check(emitted == interfaces.size) { "Emitted $emitted of ${interfaces.size} interfaces" }
        outputFile.parentFile.mkdirs()
        outputFile.writeText(out.toString())
        logger.lifecycle("Wrote $emitted payload interfaces to ${outputFile.relativeTo(projectRoot)}")
    }
}

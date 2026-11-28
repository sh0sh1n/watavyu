import net.ltgt.gradle.errorprone.errorprone
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    application
    jacoco
    pmd
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.versions)
}

group = "com.sh0sh1n"
version = "0.1.0-SNAPSHOT"

// OpenJFX Media is the video backend. This fork targets Apple Silicon macOS only:
// the patched native library in third_party/openjfx-media-avf is arm64.
val javafxVersion = libs.versions.javafx.get()
val javafxPlatform = "mac-aarch64"
val javafxModules = listOf("base", "graphics", "media", "swing")
check(System.getProperty("os.name").startsWith("Mac") && System.getProperty("os.arch") == "aarch64") {
    "This build targets Apple Silicon macOS (OpenJFX $javafxPlatform)."
}

repositories {
    mavenCentral()
}

sourceSets {
    create("integrationTest") {
        // Integration tests build windows as the unit tests do, with their helpers (TestSpreadsheets).
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
}
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

// The published javafx-media jar with libjfxmedia_avf.dylib replaced by the
// patched build. Everything else in the jar, including module-info, is upstream.
// Mockito mocks through an inline Java agent. JDK 21+ warns about agents attached at
// runtime (and future JDKs will refuse them), so test JVMs load it with -javaagent.
val mockitoAgent: Configuration = configurations.create("mockitoAgent") {
    isTransitive = false
}

val stockJavafxMedia: Configuration = configurations.create("stockJavafxMedia") {
    isTransitive = false
}

val patchedJavafxMedia = tasks.register<Jar>("patchedJavafxMedia") {
    description = "Repackages javafx-media with the patched AVFoundation library."
    destinationDirectory.set(layout.buildDirectory.dir("javafx"))
    archiveFileName.set("javafx-media-$javafxVersion-watavyu-$javafxPlatform.jar")
    from(stockJavafxMedia.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("libjfxmedia_avf.dylib", "META-INF/MANIFEST.MF")
    }
    from("third_party/openjfx-media-avf/libjfxmedia_avf.dylib")
}

dependencies {
    implementation(libs.miglayout.swing)
    implementation(libs.commons.io)
    implementation(libs.flatlaf)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.jackson.databind)

    for (module in javafxModules - "media") {
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform")
    }
    stockJavafxMedia("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation(files(patchedJavafxMedia))

    errorprone(libs.errorprone.core)

    testImplementation(libs.testng)
    testImplementation(libs.mockito.core)
    // Validates project files against the published JSON Schema. It uses Jackson 3 (tools.jackson), which does not
    // clash with the app's Jackson 2; its YAML support is excluded because the schema is JSON.
    testImplementation(libs.json.schema.validator) {
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
    }
    mockitoAgent(libs.mockito.core)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("com.sh0sh1n.watavyu.Watavyu")
    applicationDefaultJvmArgs = listOf("-Xmx1g")
}

// OpenJFX is only supported from the module path; the rest of the app stays on the
// class path. JFXPanel lives in javafx.swing, MediaPlayer in javafx.media.
fun JavaForkOptions.useJavafxModules(classpath: FileCollection): FileCollection {
    val javafxJars = classpath.filter { it.name.startsWith("javafx-") }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--module-path", javafxJars.asPath,
            "--add-modules", "javafx.swing,javafx.media",
            "--enable-native-access=javafx.graphics,javafx.media",
        )
    })
    return classpath.filter { !it.name.startsWith("javafx-") }
}

tasks.named<JavaExec>("run") {
    classpath = useJavafxModules(classpath)
}

// The Gradle project version is the only version; WatavyuVersion reads it from this resource.
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("com/sh0sh1n/watavyu/version.properties") {
        expand("version" to version)
    }
    // CITATION.cff at the repository root is what GitHub's "Cite this repository" reads; the app reads the same file.
    from("CITATION.cff")
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
    // Tests read and write settings here, never in the user's ~/Library/Application Support/Watavyu.
    systemProperty("watavyu.settingsDir", layout.buildDirectory.dir("test-settings/$name").get().asFile.absolutePath)
    // and log here, never in the user's ~/Library/Logs/Watavyu.
    systemProperty("watavyu.logDir", layout.buildDirectory.dir("test-logs/$name").get().asFile.absolutePath)
    // Tests see the light palette whatever the Mac's appearance, and never ask macOS for it.
    systemProperty("watavyu.appearance", "light")
}

tasks.test {
    useTestNG()
    systemProperty("testPath", file("src/test/resources").absolutePath)
    systemProperty("expectedVersion", project.version.toString())
    classpath = useJavafxModules(classpath)
}

// Playback tests drive the real OpenJFX player and need an awake display; see BUILDING.md.
tasks.register<Test>("integrationTest") {
    description = "Runs OpenJFX playback integration tests against a generated MP4 fixture."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = useJavafxModules(sourceSets["integrationTest"].runtimeClasspath)
    useTestNG()
    systemProperty("fixtureDir", layout.buildDirectory.dir("playback-fixtures").get().asFile.absolutePath)
    shouldRunAfter(tasks.test)
}

// Analysis reports that measure the work in TODO.md. ./gradlew analyze writes all of them under build/reports.
// Each check that reaches zero findings fails the build from then on, so it stays at zero (AGENTS.md).

// Every compile fails on a warning: every javac lint category but serial, and every Error Prone check at its default
// severity. serial is off because the views are Serializable only by inheriting from Swing and are never serialized;
// it has about 130 warnings (TODO.md).
val compiles = tasks.withType<JavaCompile>()
compiles.configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

// ./gradlew compileJava compileTestJava -PerrorpronePatch=CheckA,CheckB rewrites the sources in place with Error
// Prone's suggested fixes for those checks. Review the result like any other change.
providers.gradleProperty("errorpronePatch").orNull?.let { checks ->
    compiles.configureEach {
        options.errorprone.errorproneArgs.addAll("-XepPatchChecks:$checks", "-XepPatchLocation:IN_PLACE")
    }
}

pmd {
    toolVersion = libs.versions.pmd.get()
    sourceSets = listOf(project.sourceSets.main.get())
    ruleSets = emptyList()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    isIgnoreFailures = true
    isConsoleOutput = false
}

// Every PMD rule in config/pmd/ruleset.xml fails the build except these, which are not at zero yet. A rule leaves
// this list when it reaches zero, and a rule added to the ruleset is gated unless it is listed here.
val pmdUngatedRules = setOf(
    // Size and complexity
    "GodClass", "TooManyMethods", "CyclomaticComplexity", "CognitiveComplexity", "CouplingBetweenObjects",
    "NcssCount", "ExcessiveImports", "TooManyFields",
    // Catches at a boundary that must report or survive any failure: a clock tick (ClockTimer.every), parsing an
    // untrusted MP4 file (JavaFxVideoPlugin.readFrames) and the thread that opens media (VideoController.openStream).
    "AvoidCatchingGenericException", "AvoidCatchingThrowable",
)
val pmdGate = tasks.register("pmdGate") {
    description = "Fails if PMD finds anything under a rule that has reached zero findings."
    group = "verification"
    val report = layout.buildDirectory.file("reports/pmd/main.xml")
    dependsOn(tasks.named("pmdMain"))
    inputs.file(report)
    doLast {
        val found = Regex("<violation [^>]*rule=\"([^\"]+)\"").findAll(report.get().asFile.readText())
            .map { it.groupValues[1] }.filter { it !in pmdUngatedRules }.toList()
        if (found.isNotEmpty()) {
            throw GradleException("PMD found ${found.size} issue(s) under gated rules: " +
                "${found.distinct()}; see build/reports/pmd/main.html")
        }
    }
}
tasks.check { dependsOn(pmdGate) }

// SpotBugs fails the build on any finding: every category reached zero (AGENTS.md: checks only ratchet up).
spotbugs {
    ignoreFailures.set(false)
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
    showProgress.set(false)
}
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = name == "spotbugsMain"
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}


tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register("analyze") {
    description = "Writes coverage, PMD and SpotBugs reports under build/reports."
    group = "verification"
    dependsOn(tasks.jacocoTestReport, tasks.named("pmdMain"), tasks.named("spotbugsMain"))
}

// Only stable releases count as available updates in ./gradlew dependencyUpdates.
tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        Regex("(?i).*[.-](alpha|beta|rc|cr|m|ea|preview)[.\\d+-]*$").matches(candidate.version)
    }
}

// Usage: ./gradlew playbackBenchmark -Pmedia=/path/to/video.mp4 [-Pseeks=200]
tasks.register<JavaExec>("playbackBenchmark") {
    description = "Measures seek latency, frame accuracy, stepping and playback CPU for one video."
    group = "verification"
    mainClass.set("com.sh0sh1n.watavyu.playback.javafx.PlaybackBenchmark")
    classpath = useJavafxModules(sourceSets["integrationTest"].runtimeClasspath)
    args(providers.gradleProperty("media").orElse("").get(), providers.gradleProperty("seeks").orElse("200").get())
}

// Distribution (BUILDING.md › Distribution). ./gradlew distApp builds build/dist/app/Watavyu.app, which carries its
// own Java runtime; distSign signs a copy, distCheck launches it to check it, distDmg puts it in a disk image and
// distNotarize has Apple notarize that. A signed app may load only libraries signed with it, and
// notarization refuses unsigned code inside jars, so no native library is left in a jar for Java to unpack as it
// runs: JavaFX's go into the runtime's lib folder, where JavaFX looks before unpacking its own, and FlatLaf's into
// the app's natives folder, which flatlaf.nativeLibraryPath names.
val appName = "Watavyu"
val bundleId = "com.sh0sh1n.watavyu"
val appVersion = version.toString().removeSuffix("-SNAPSHOT")
// The oldest macOS the app runs on, the one it is built and tested on (owner, 2026-11-27).
val minimumMacOS = "27.0"
val distDir = layout.buildDirectory.dir("dist")
val jdkHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    .map { it.metadata.installationPath.asFile.absolutePath }
// The JDK's modules the app and its libraries use (jdeps --print-module-deps), and the JavaFX modules it plays with.
val distModules = listOf(
    "java.base", "java.compiler", "java.desktop", "java.logging", "java.management", "java.naming", "java.rmi",
    "java.scripting", "java.sql", "jdk.unsupported", "jdk.unsupported.desktop", "javafx.swing", "javafx.media",
)
val nativeLibrary = Regex(""".*\.(dylib|jnilib|so|dll)$""")

/** Returns whether a file is Mach-O code, an executable or a library, as a signature covers it. */
fun isMachO(file: File): Boolean {
    if (!file.isFile || file.length() < 4 || file.name.endsWith(".class") || file.name.endsWith(".jar")) {
        return false
    }
    val head = file.inputStream().use { it.readNBytes(8) }
    val magic = head.take(4).joinToString("") { "%02x".format(it) }
    // A universal binary's magic is a class file's too: a universal binary goes on with the count of its
    // architectures, a class file with its version, 45 or more.
    val next = if (head.size == 8) ByteBuffer.wrap(head, 4, 4).int else 0
    // 64-bit Mach-O as an Apple Silicon Mac writes it, and a universal binary.
    return magic == "cffaedfe" || magic == "cafebabe" && next in 1..44
}

/** Orders macOS versions such as 11.0 and 14.10 by their numbers. */
val macOSVersions = Comparator<String> { a, b ->
    val x = a.split('.').map(String::toInt)
    val y = b.split('.').map(String::toInt)
    (0 until maxOf(x.size, y.size)).map { x.getOrElse(it) { 0 }.compareTo(y.getOrElse(it) { 0 }) }
        .firstOrNull { it != 0 } ?: 0
}

/** Returns the oldest macOS a Mach-O file runs on, as it was built for (its LC_BUILD_VERSION), such as 11.0. */
fun builtForMacOS(file: File, exec: ExecOperations): String {
    val output = ByteArrayOutputStream()
    exec.exec {
        commandLine("otool", "-l", file.absolutePath)
        standardOutput = output
    }
    return Regex("""minos (\S+)""").find(output.toString())?.groupValues?.get(1)
        ?: throw GradleException("${file.name} does not say the macOS it was built for")
}

/** The services a task action uses in place of the project, which task actions should not reach for. */
interface DistServices {
    @get:Inject val fs: FileSystemOperations
    @get:Inject val exec: ExecOperations
}
val dist = objects.newInstance<DistServices>()

sourceSets.create("dist") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations.named("distImplementation") { extendsFrom(configurations.implementation.get()) }

/**
 * Copies a jar without its native libraries, writing those that [keep] accepts to [natives] by their own names.
 */
fun copyJarWithoutNatives(jar: File, into: File, natives: File, keep: (String) -> Boolean) {
    ZipFile(jar).use { zip ->
        ZipOutputStream(File(into, jar.name).outputStream().buffered()).use { out ->
            for (entry in zip.entries()) {
                if (nativeLibrary.matches(entry.name)) {
                    if (keep(entry.name)) {
                        File(natives, entry.name.substringAfterLast('/')).outputStream().use { file ->
                            zip.getInputStream(entry).use { it.copyTo(file) }
                        }
                    }
                    continue
                }
                out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                zip.getInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }
}

val distJars = tasks.register("distJars") {
    description = "Copies the app's jars for its bundle, their native libraries taken out to be signed as files."
    group = "distribution"
    val runtimeJars: FileCollection = configurations.runtimeClasspath.get()
    val appJar = tasks.jar.flatMap { it.archiveFile }
    val output = distDir.map { it.dir("jars") }
    inputs.files(runtimeJars, appJar)
    outputs.dir(output)
    doLast {
        val root = output.get().asFile
        root.deleteRecursively()
        // JavaFX's jars become the runtime's modules; the rest, the app's own jar first, go in the app's folder.
        val javafx = File(root, "javafx").apply { mkdirs() }
        val javafxNatives = File(root, "javafx-natives").apply { mkdirs() }
        val app = File(root, "app").apply { mkdirs() }
        val appNatives = File(app, "natives").apply { mkdirs() }
        copyJarWithoutNatives(appJar.get().asFile, app, appNatives) { false }
        for (jar in runtimeJars) {
            if (jar.name.startsWith("javafx-")) {
                copyJarWithoutNatives(jar, javafx, javafxNatives) { it.endsWith(".dylib") }
            } else {
                // Only this Mac's: FlatLaf's jar holds libraries for every platform.
                copyJarWithoutNatives(jar, app, appNatives) { it.endsWith("macos-arm64.dylib") }
            }
        }
    }
}

val distRuntime = tasks.register<Exec>("distRuntime") {
    description = "Links the app's Java runtime, with JavaFX in it and JavaFX's native libraries in its lib folder."
    group = "distribution"
    dependsOn(distJars)
    val jars = distDir.map { it.dir("jars") }
    val runtime = distDir.map { it.dir("runtime") }
    inputs.dir(jars)
    outputs.dir(runtime)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--module-path", jars.get().dir("javafx").asFile.absolutePath,
            "--add-modules", distModules.joinToString(","),
            "--strip-debug", "--no-header-files", "--no-man-pages", "--strip-native-commands",
            "--output", runtime.get().asFile.absolutePath,
        )
    })
    doFirst {
        executable = jdkHome.get() + "/bin/jlink"
        dist.fs.delete { delete(runtime) }
    }
    doLast {
        dist.fs.copy {
            from(jars.get().dir("javafx-natives"))
            into(runtime.get().dir("lib"))
        }
    }
}

val distIconset = tasks.register<JavaExec>("distIconset") {
    description = "Draws the app icon as an iconset."
    group = "distribution"
    val iconset = distDir.map { it.dir("$appName.iconset") }
    classpath = sourceSets["dist"].runtimeClasspath
    mainClass.set("com.sh0sh1n.watavyu.dist.IconsetWriter")
    systemProperty("java.awt.headless", "true")
    outputs.dir(iconset)
    argumentProviders.add(CommandLineArgumentProvider { listOf(iconset.get().asFile.absolutePath) })
}

val distIcon = tasks.register<Exec>("distIcon") {
    description = "Turns the iconset into the app's .icns file."
    group = "distribution"
    dependsOn(distIconset)
    val iconset = distDir.map { it.dir("$appName.iconset") }
    val icns = distDir.map { it.file("$appName.icns") }
    inputs.dir(iconset)
    outputs.file(icns)
    executable = "iconutil"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("-c", "icns", "-o", icns.get().asFile.absolutePath, iconset.get().asFile.absolutePath)
    })
}

val distApp = tasks.register<Exec>("distApp") {
    description = "Builds Watavyu.app, unsigned, with its own Java runtime."
    group = "distribution"
    dependsOn(distRuntime, distIcon)
    val jars = distDir.map { it.dir("jars/app") }
    val runtime = distDir.map { it.dir("runtime") }
    val icns = distDir.map { it.file("$appName.icns") }
    val output = distDir.map { it.dir("app") }
    val mainJar = tasks.jar.flatMap { it.archiveFileName }
    inputs.dir(jars)
    inputs.dir(runtime)
    inputs.file(icns)
    // Written into the Info.plist after jpackage, so a change rebuilds the app.
    inputs.property("version", appVersion)
    inputs.property("minimumMacOS", minimumMacOS)
    outputs.dir(output)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--type", "app-image",
            "--name", appName,
            // jpackage refuses a version starting with 0, which macOS takes; the real one is written after.
            "--app-version", "1",
            "--vendor", "sh0sh1n LLC",
            "--copyright", "The Watavyu Contributors. Free software under the GNU GPL version 3.",
            "--description", "Code behaviour in video",
            "--mac-package-identifier", bundleId,
            "--mac-package-name", appName,
            "--mac-app-category", "productivity",
            "--icon", icns.get().asFile.absolutePath,
            "--runtime-image", runtime.get().asFile.absolutePath,
            "--input", jars.get().asFile.absolutePath,
            "--main-jar", mainJar.get(),
            "--main-class", application.mainClass.get(),
            "--java-options", "-Xmx1g",
            "--java-options", "--enable-native-access=javafx.graphics,javafx.media,ALL-UNNAMED",
            "--java-options", "-Dflatlaf.nativeLibraryPath=\$APPDIR/natives",
            // The app's settings domain (MacOS.BUNDLE_ID_PROPERTY), so the identifier is written only here.
            "--java-options", "-Dwatavyu.bundleId=$bundleId",
            "--dest", output.get().asFile.absolutePath,
        )
    })
    doFirst {
        executable = jdkHome.get() + "/bin/jpackage"
        dist.fs.delete { delete(output) }
    }
    doLast {
        val app = output.get().dir("$appName.app").asFile
        // No code in the app may need a macOS newer than the oldest the app declares.
        val tooNew = app.walk().filter(::isMachO)
            .map { it to builtForMacOS(it, dist.exec) }
            .filter { macOSVersions.compare(it.second, minimumMacOS) > 0 }
            .map { "${it.first.name} needs macOS ${it.second}" }.toList()
        if (tooNew.isNotEmpty()) {
            throw GradleException("The app declares macOS $minimumMacOS, but ${tooNew.joinToString()}")
        }
        val plist = File(app, "Contents/Info.plist").absolutePath
        val changes = listOf(
            "Set :CFBundleShortVersionString $appVersion",
            "Set :CFBundleVersion $appVersion",
            "Set :LSMinimumSystemVersion $minimumMacOS",
        )
        for (change in changes) {
            dist.exec.exec { commandLine("/usr/libexec/PlistBuddy", "-c", change, plist) }
        }
        // jpackage asks for the microphone by default; the app records nothing. A jpackage that stops asking is fine.
        dist.exec.exec {
            commandLine("/usr/libexec/PlistBuddy", "-c", "Delete :NSMicrophoneUsageDescription", plist)
            isIgnoreExitValue = true
        }
    }
}

// Signing (BUILDING.md › Distribution): -PsigningIdentity names the Developer ID Application certificate in the
// keychain; without it the app is signed ad hoc, which runs on this Mac but which macOS blocks on any other.
val signingIdentity = providers.gradleProperty("signingIdentity").orElse("-")
val notaryProfile = providers.gradleProperty("notaryProfile").orElse("watavyu-notary")

/**
 * Signs code with a certificate, the hardened runtime and a secure timestamp, which notarization requires, or ad hoc,
 * without them: the hardened runtime loads only libraries signed by the app's team, and an ad hoc signature has none.
 */
fun codesign(exec: ExecOperations, identity: String, target: File, vararg options: String) {
    val signing = if (identity == "-") listOf("--timestamp=none") else listOf("--options", "runtime", "--timestamp")
    exec.exec {
        commandLine(listOf("codesign", "--force", "--sign", identity) + signing + options + target.absolutePath)
    }
}

val distSign = tasks.register("distSign") {
    description = "Signs a copy of Watavyu.app: each library and executable, then its runtime, then the app."
    group = "distribution"
    dependsOn(distApp)
    val unsigned = distDir.map { it.dir("app/$appName.app") }
    val signed = distDir.map { it.dir("signed/$appName.app") }
    val entitlements = file("src/dist/entitlements.plist")
    inputs.dir(unsigned)
    inputs.file(entitlements)
    inputs.property("identity", signingIdentity)
    outputs.dir(distDir.map { it.dir("signed") })
    doLast {
        val app = signed.get().asFile
        dist.fs.delete { delete(app.parentFile) }
        app.parentFile.mkdirs()
        dist.exec.exec { commandLine("ditto", unsigned.get().asFile.absolutePath, app.absolutePath) }
        val identity = signingIdentity.get()
        // Inside out, as a signature covers what it holds: the code first, deepest first, then the runtime bundle,
        // then the app, whose launcher is signed with it and carries the entitlements.
        val launcher = File(app, "Contents/MacOS/$appName")
        app.walk().filter { isMachO(it) && it != launcher }.sortedByDescending { it.path.count { c -> c == '/' } }
            .forEach { codesign(dist.exec, identity, it) }
        codesign(dist.exec, identity, File(app, "Contents/runtime"))
        codesign(dist.exec, identity, app, "--entitlements", entitlements.absolutePath)
        dist.exec.exec { commandLine("codesign", "--verify", "--deep", "--strict", "--verbose=2", app.absolutePath) }
    }
}

/** Runs a command and returns what it prints, errors included, failing if it fails unless [mayFail]. */
fun output(exec: ExecOperations, vararg command: String, mayFail: Boolean = false): String {
    val out = ByteArrayOutputStream()
    exec.exec {
        commandLine(*command)
        standardOutput = out
        errorOutput = out
        isIgnoreExitValue = mayFail
    }
    return out.toString()
}

val distCheck = tasks.register("distCheck") {
    description = "Checks the signed Watavyu.app, then launches it to play a video and checks what it loaded."
    group = "verification"
    dependsOn(distSign)
    val signed = distDir.map { it.dir("signed/$appName.app") }
    val entitlements = file("src/dist/entitlements.plist")
    val video = file("src/test/resources/video/cfr-bframes.mp4")
    val work = layout.buildDirectory.dir("dist-check")
    inputs.dir(signed)
    inputs.file(video)
    inputs.property("identity", signingIdentity)
    outputs.upToDateWhen { false }
    doLast {
        val app = signed.get().asFile
        val problems = mutableListOf<String>()
        fun expect(ok: Boolean, problem: String) {
            if (!ok) problems.add(problem)
        }

        // Signed, every piece of code, with the hardened runtime and only the entitlements in the file.
        output(dist.exec, "codesign", "--verify", "--deep", "--strict", app.absolutePath)
        val adHoc = signingIdentity.get() == "-"
        val flags = Regex("""flags=\S*\(([^)]*)\)""").find(output(dist.exec, "codesign", "-dv", app.absolutePath))
            ?.groupValues?.get(1).orEmpty().split(',')
        expect(adHoc || "runtime" in flags, "signed without the hardened runtime: $flags")
        expect(adHoc == ("adhoc" in flags), "signed ${if (adHoc) "with a certificate" else "ad hoc"}: $flags")
        val granted = Regex("<key>([^<]+)</key>")
        val given = granted
            .findAll(output(dist.exec, "codesign", "-d", "--entitlements", "-", "--xml", app.absolutePath))
            .map { it.groupValues[1] }.toSet()
        val wanted = granted.findAll(entitlements.readText()).map { it.groupValues[1] }.toSet()
        expect(given == wanted, "entitlements $given, not $wanted")

        // No native library left in a jar, where it would go unsigned.
        app.walk().filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { nativeLibrary.matches(it.name) }
                    .forEach { problems.add("${jar.name} holds ${it.name}") }
            }
        }

        // The app as macOS and the app itself know it.
        val plist = File(app, "Contents/Info.plist").absolutePath
        fun plistValue(key: String) = runCatching {
            output(dist.exec, "/usr/libexec/PlistBuddy", "-c", "Print :$key", plist).trim()
        }.getOrNull()
        expect(plistValue("CFBundleIdentifier") == bundleId, "bundle identifier ${plistValue("CFBundleIdentifier")}")
        val shownVersion = plistValue("CFBundleShortVersionString")
        expect(shownVersion == appVersion, "version $shownVersion, not $appVersion")
        expect(plistValue("NSMicrophoneUsageDescription") == null, "asks for the microphone")
        val oldestMacOS = plistValue("LSMinimumSystemVersion")
        expect(oldestMacOS == minimumMacOS, "runs on macOS $oldestMacOS, not $minimumMacOS")
        val launcherOptions = File(app, "Contents/app/$appName.cfg").readText()
        expect(launcherOptions.contains("-Dwatavyu.bundleId=$bundleId"), "the app is not told its bundle identifier")

        // Launched with a project holding a video, it loads only libraries from inside itself, and plays the video.
        val dir = work.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        video.copyTo(File(dir, video.name))
        val project = File(dir, "check.watavyu.json")
        project.writeText("""{"format": "watavyu", "version": 1, "columns": [{"name": "media_1", "type": "media",
            |"settings": {"hidden": false, "locked": false}, "cells": [{"onset": "00:00:00:000",
            |"settings": {"volume": 0.0}, "source": {"file": "${video.name}"}}]}]}""".trimMargin())
        val process = ProcessBuilder(File(app, "Contents/MacOS/$appName").absolutePath, project.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(dir, "out.txt")).apply {
                // Settings and the log stay here, never in the user's.
                environment()["JAVA_TOOL_OPTIONS"] =
                    "-Dwatavyu.settingsDir=${dir.resolve("settings")} -Dwatavyu.logDir=${dir.resolve("logs")}"
            }.start()
        val logFile = dir.resolve("logs/watavyu.log")
        val opened = listOf(File(dir, video.name).canonicalPath, File(dir, video.name).absolutePath)
            .map { "Opened $it" }
        fun videoOpened() = logFile.isFile && logFile.readText().let { log -> opened.any { log.contains(it) } }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
            while (process.isAlive && System.nanoTime() < deadline && !videoOpened()) {
                Thread.sleep(500)
            }
            expect(process.isAlive, "the app quit; see ${dir.resolve("out.txt")}")
            expect(videoOpened(), "the video did not open; see $logFile")
            val lsof = ProcessBuilder("lsof", "-p", process.pid().toString(), "-Fn").start()
            val loaded = lsof.inputStream.bufferedReader().readLines().filter { it.startsWith("n") }
                .map { it.substring(1) }.filter { it.endsWith(".dylib") }
            lsof.waitFor()
            expect(loaded.any { it.endsWith("/libjfxmedia_avf.dylib") }, "the video library is not loaded")
            loaded.filter { !it.startsWith("/System/") && !it.startsWith("/usr/lib/") && !it.startsWith(app.path) }
                .forEach { problems.add("loaded from outside the app: $it") }
        } finally {
            process.destroy()
            process.waitFor(10, TimeUnit.SECONDS)
            process.destroyForcibly()
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Watavyu.app is not ready:\n  " + problems.joinToString("\n  "))
        }
    }
}

val distDmg = tasks.register("distDmg") {
    description = "Puts the checked, signed Watavyu.app in a disk image, signed too, beside a link to Applications."
    group = "distribution"
    dependsOn(distCheck)
    val signed = distDir.map { it.dir("signed/$appName.app") }
    val dmg = distDir.map { it.file("$appName-$appVersion.dmg") }
    inputs.dir(signed)
    outputs.file(dmg)
    doLast {
        val staging = temporaryDir.resolve("staging")
        staging.deleteRecursively()
        staging.mkdirs()
        dist.exec.exec { commandLine("ditto", signed.get().asFile.absolutePath, staging.resolve("$appName.app").path) }
        Files.createSymbolicLink(staging.resolve("Applications").toPath(), File("/Applications").toPath())
        val image = dmg.get().asFile
        image.delete()
        dist.exec.exec {
            commandLine("hdiutil", "create", "-volname", appName, "-srcfolder", staging.path, "-format", "ULFO",
                image.absolutePath)
        }
        val identity = signingIdentity.get()
        if (identity != "-") {
            dist.exec.exec { commandLine("codesign", "--force", "--timestamp", "--sign", identity, image.absolutePath) }
        }
    }
}

val distNotarize = tasks.register("distNotarize") {
    description = "Has Apple notarize the disk image, then staples the ticket to it, so it opens without a warning."
    group = "distribution"
    dependsOn(distDmg)
    val dmg = distDir.map { it.file("$appName-$appVersion.dmg") }
    doLast {
        val image = dmg.get().asFile.absolutePath
        // notarytool fails on a submission Apple does not accept, which the status below explains.
        val submitted = output(dist.exec, "xcrun", "notarytool", "submit", image,
            "--keychain-profile", notaryProfile.get(), "--wait", "--output-format", "json", mayFail = true)
        val status = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(submitted)?.groupValues?.get(1)
        if (status != "Accepted") {
            val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(submitted)?.groupValues?.get(1)
            val log = id?.let {
                runCatching { output(dist.exec, "xcrun", "notarytool", "log", it, "--keychain-profile",
                    notaryProfile.get()) }.getOrNull()
            }
            throw GradleException("Notarization was not accepted ($status):\n$submitted\n${log.orEmpty()}")
        }
        dist.exec.exec { commandLine("xcrun", "stapler", "staple", image) }
        dist.exec.exec { commandLine("xcrun", "stapler", "validate", image) }
        dist.exec.exec { commandLine("spctl", "--assess", "--type", "open", "--context", "context:primary-signature",
            "--verbose=2", image) }
    }
}
// Before anything is built: notarizing needs the certificate, which an ad hoc build would find out only at the end.
gradle.taskGraph.whenReady {
    if (hasTask(distNotarize.get()) && signingIdentity.get() == "-") {
        throw GradleException("Notarizing needs the Developer ID Application certificate: set signingIdentity " +
            "(BUILDING.md › Distribution)")
    }
}

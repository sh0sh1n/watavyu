plugins {
    application
    jacoco
    pmd
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.versions)
}

group = "org.datavyu"
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
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
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
    implementation(libs.snakeyaml)
    implementation(variantOf(libs.miglayout) { classifier("swing") })
    implementation(libs.commons.io)
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
    mainClass.set("org.datavyu.Datavyu")
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

// The Gradle project version is the only version; DatavyuVersion reads it from this resource.
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("org/datavyu/version.properties") {
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

// Analysis reports that measure the work in TODO.md. They do not gate the build:
// Error Prone ERRORs and the tests are the gates. ./gradlew analyze writes all of them under build/reports.
// ./gradlew compileJava -PallWarnings lists every javac lint and Error Prone warning (javac stops at 100).
if (providers.gradleProperty("allWarnings").isPresent) {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xmaxwarns", "100000", "-Xlint:all,-serial,-processing"))
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

spotbugs {
    ignoreFailures.set(true)
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
    mainClass.set("org.datavyu.plugins.javafx.PlaybackBenchmark")
    classpath = useJavafxModules(sourceSets["integrationTest"].runtimeClasspath)
    args(providers.gradleProperty("media").orElse("").get(), providers.gradleProperty("seeks").orElse("200").get())
}

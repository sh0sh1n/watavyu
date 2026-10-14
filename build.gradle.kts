plugins {
    application
    id("net.ltgt.errorprone") version "5.1.1"
}

group = "org.datavyu"
version = "0.1.0-SNAPSHOT"

// OpenJFX Media is the video backend. This fork targets Apple Silicon macOS only:
// the patched native library in third_party/openjfx-media-avf is arm64.
val javafxVersion = "27"
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
    implementation("net.java.dev.jna:jna:5.2.0")
    implementation("org.jruby:jruby-complete:9.1.13.0")
    implementation("org.jdesktop:appframework:1.0.3")
    implementation("org.swinglabs:swing-layout:1.0.3")
    implementation("org.yaml:snakeyaml:1.18")
    implementation("com.miglayout:miglayout:3.7.4:swing")
    implementation("commons-io:commons-io:2.5")
    implementation("org.apache.commons:commons-lang3:3.6")
    implementation("org.apache.commons:commons-text:1.2")
    implementation("com.google.guava:guava:27.1-jre")
    implementation("com.github.jbytecode:RCaller:3.0")
    implementation("org.apache.logging.log4j:log4j-api:2.11.2")
    implementation("org.apache.logging.log4j:log4j-core:2.11.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.9.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.9.9")
    implementation("com.fasterxml.jackson.core:jackson-core:2.9.9")

    for (module in javafxModules - "media") {
        implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform")
    }
    stockJavafxMedia("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
    implementation(files(patchedJavafxMedia))

    errorprone("com.google.errorprone:error_prone_core:2.50.0")

    testImplementation("org.testng:testng:6.11")
    testImplementation("org.mockito:mockito-core:5.24.0")
    mockitoAgent("org.mockito:mockito-core:5.24.0")
    testImplementation("org.easytesting:fest-swing:1.2a4")
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

// Usage: ./gradlew playbackBenchmark -Pmedia=/path/to/video.mp4 [-Pseeks=200]
tasks.register<JavaExec>("playbackBenchmark") {
    description = "Measures seek latency, frame accuracy, stepping and playback CPU for one video."
    group = "verification"
    mainClass.set("org.datavyu.plugins.javafx.PlaybackBenchmark")
    classpath = useJavafxModules(sourceSets["integrationTest"].runtimeClasspath)
    args(providers.gradleProperty("media").orElse("").get(), providers.gradleProperty("seeks").orElse("200").get())
}

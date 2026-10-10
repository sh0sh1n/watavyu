plugins {
    application
}

group = "org.datavyu"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.jfrog.org/artifactory/oss-release-local")
    }
    maven {
        url = uri("https://dl.bintray.com/datavyu/datavyu")
    }
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
    implementation("com.brsanthu:google-analytics-java:2.0.0")

    runtimeOnly("org.datavyu:libNativeOSXCanvas:0.92")
    implementation("org.datavyu:ffmpeg-plugin:0.24:mac")

    testImplementation("org.testng:testng:6.11")
    testImplementation("org.mockito:mockito-core:2.10.0")
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

tasks.processResources {
    exclude("org/datavyu/resources/Build.properties")
    from("src/main/resources/org/datavyu/resources/Build_Template.properties") {
        into("org/datavyu/resources")
        rename { "Build.properties" }
        expand("buildNumber" to project.version)
    }
}

tasks.test {
    useTestNG()
    systemProperty("testPath", file("src/test/resources").absolutePath)
}
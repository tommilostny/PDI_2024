// see https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/configuration/overview/#gradle-build-script

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// import external variables from gradle.properties
val externalGroup: String by project
val externalVersion: String by project
val externalMainClassName: String by project
val externalFlinkVersion: String by project
val externalKryoVersion: String by project

plugins {
    // Apply the java plugin to add support for Java
    id("java")
    // Apply the application plugin to add support for building an application
    id("application")
    // Apply the Maven publish plugin to publish resulting artefacts into a Maven repository
    id("maven-publish")
    // Apply the shadow plugin to produce fat JARs
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = externalGroup
version = externalVersion

application {
    // Define the main class for the application
    mainClass.set(externalMainClassName)
    // https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/advanced/logging/#configuring-log4j-2
    // https://github.com/apache/flink/raw/master/flink-dist/src/main/flink-bin/conf/log4j-console.properties with removed appender.rolling to disable log.file
    // NOTE: the log4j.configurationFile loads also from resources, not just from the current dir, which is utilized in the app distribution to load ./src/main/resources/log4j.properties
    applicationDefaultJvmArgs = listOf("-Dlog4j.configurationFile=log4j-console.properties")
}

// NOTE: We cannot use "compileOnly(" or "shadow" configurations since then we could not run code)
// in the IDE or with "gradle run". We also cannot exclude isTransitive = the
// shadowJar yet (see https://github.com/johnrengelman/shadow/issues/159).
// -> Explicitly define the // libraries we want to be included in the "flinkShadowJar" configuration!
val flinkShadowJar by configurations.creating // dependencies which go into the shadowJar
configurations {
    // always exclude these (also from isTransitive = Flink
    flinkShadowJar.exclude(group = "org.apache.flink", module = "force-shading")
    flinkShadowJar.exclude(group = "com.google.code.findbugs", module = "jsr305")
    flinkShadowJar.exclude(group = "org.slf4j")
    flinkShadowJar.exclude(group = "org.apache.logging.log4j")
}

// prevent the use of non reproducible dependencies
configurations.all {
    resolutionStrategy {
        failOnNonReproducibleResolution()
        // to prevent version conflicts due to libs with dependencies on various Kryo versions
        force("com.esotericsoftware.kryo:kryo:$externalKryoVersion")
    }
}

dependencies {
    // Compile-time dependencies that should NOT be part of the shadow (uber) jar and are provided in the lib folder of Flink
    implementation("org.apache.flink:flink-clients:$externalFlinkVersion")
    implementation("org.apache.flink:flink-streaming-java:$externalFlinkVersion")
    implementation("org.apache.flink:flink-connector-files:$externalFlinkVersion")
    // Dependencies that should be part of the shadow jar, e.g. connectors. These must be in the flinkShadowJar configuration!
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.24.1")
    runtimeOnly("org.apache.logging.log4j:log4j-api:2.24.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.24.1")
    // Add test dependencies here.
    testImplementation(platform("org.junit:junit-bom:5.11.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "build-repository"
            url = uri(layout.buildDirectory.dir("mvn-repo"))
        }
    }
}

// make compileOnly(dependencies available for tests:)
sourceSets {
    main {
        compileClasspath += flinkShadowJar
        runtimeClasspath += flinkShadowJar
    }
    test {
        compileClasspath += flinkShadowJar
        runtimeClasspath += flinkShadowJar
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    // Pass all system properties to the application and the start scripts (overwrite previously defined defaults if any)
    systemProperties(System.getProperties().mapKeys { it.key as String })
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Build-Jdk" to System.getProperty("java.version"),
            "Main-Class" to externalMainClassName,
        )
    }
}

// ensure proper reproducibility of the genereated .jar files
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(flinkShadowJar)
}

// use dynamic classpath instead of a list of JARs; prevents "The input line is too long" on Windows
tasks.withType<CreateStartScripts> {
    classpath = files("lib/*")
    doNotTrackState("CreateStartScripts needs to re-run every time as Windows cannot stat classpath files by shell pattern.")
}

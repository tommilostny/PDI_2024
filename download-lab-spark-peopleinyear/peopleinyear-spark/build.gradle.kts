// see https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/configuration/overview/#gradle-build-script

// import external variables from gradle.properties
val externalGroup: String by project
val externalVersion: String by project
val externalMainClassName: String by project
val externalSparkVersion: String by project

plugins {
    // Apply the scala plugin to add support for Scala
    id("scala")
    // Apply the application plugin to add support for building an application
    id("application")
    // Apply the Maven publish plugin to publish resulting artefacts into a Maven repository
    id("maven-publish")
}

group = externalGroup
version = externalVersion

application {
    // Define the main class for the application
    mainClass.set(externalMainClassName)
    // fix JDK 17 error in Spark runtime
    // https://docs.gradle.org/current/javadoc/org/gradle/api/JavaVersion.html#VERSION_17
    if (JavaVersion.current() >= JavaVersion.VERSION_17) {
        project.logger.warn("The application will run with add-opens JVM args to fix Spark on JDK 17.")
        applicationDefaultJvmArgs +=
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
                "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
                "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
                "--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED",
            )
    }
}

// prevent the use of non reproducible dependencies
configurations.all {
    resolutionStrategy {
        failOnNonReproducibleResolution()
        // there are many version conflicts in libraries of the Spark dependencies; all can be resolved automatically, so we will ignore them here
    }
}

dependencies {
    // Scala version may be the only way to ensure compatibility with Hadoop distributions.
    // Both org.scala-lang:scala-library version and org.apache.spark:spark-sql name need to be set according to the Scala version.
    // NixOS v20.09: Spark 2.4.4 + Scala 2.11.12 @ Java 1.8.0_265
    // NixOS v22.05: Spark 3.2.2 + Scala 2.12.15 @ Java 1.8.0_322; not compatible with JDK 11+
    // NixOS v24.05: Spark 3.5.1 + Scala 2.12.18 @ Java 11.0.23
    // Cloudera QuickStarts for CDH 5.13: ? @ Java 1.7.0_67
    // In Scala 2.11, the Scala compiler always compiles to Java 6 compatible bytecode.
    // In Scala 2.12, the Scala compiler always compiles to Java 8 compatible bytecode.
    // See https://docs.gradle.org/current/userguide/scala_plugin.html#sec:scala_cross_compilation
    implementation("org.scala-lang:scala-library:2.12.18")
    // for corresponding Hadoop version to set in gradle.properties, check: grep -F hadoop-main gradle/verification-metadata.xml
    implementation("org.apache.spark:spark-sql_2.12:$externalSparkVersion")
    // test dependencies
    testImplementation(platform("org.junit:junit-bom:5.11.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // for compatibility with Hadoop distributions such as Cloudera QuickStarts for CDH 5.13 (Java 1.7.0_67), however, increased to the minimal 1.8 version of JDK available in Gradle
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

// use dynamic classpath instead of a list of JARs; prevents "The input line is too long" on Windows
tasks.withType<CreateStartScripts> {
    classpath = files("lib/*")
    doNotTrackState("CreateStartScripts needs to re-run every time as Windows cannot stat classpath files by shell pattern.")
}

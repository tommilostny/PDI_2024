// see https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/configuration/overview/#gradle-build-script

// import external variables from gradle.properties
val externalGroup: String by project
val externalVersion: String by project
val externalMainClassName: String by project
val externalMainClassNameInCluster: String by project
val externalHadoopVersion: String by project

plugins {
    // Apply the java plugin to add support for Java
    id("java")
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
}

// prevent the use of non reproducible dependencies
configurations.all {
    resolutionStrategy {
        failOnNonReproducibleResolution()
        // there are many version conflicts in libraries of the Hadoop dependencies; all can be resolved automatically, so we will ignore them here
    }
}

dependencies {
    // Apache Hadoop
    implementation("org.apache.hadoop:hadoop-mapreduce-client-core:$externalHadoopVersion")
    implementation("org.apache.hadoop:hadoop-common:$externalHadoopVersion")
    implementation("org.apache.hadoop:hadoop-minicluster:$externalHadoopVersion")
    // Hadoop Mini DFS cluster deps
    implementation("org.mockito:mockito-core:5.14.2")
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
            "Main-Class" to externalMainClassNameInCluster,
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

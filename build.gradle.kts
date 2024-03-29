import Build_gradle.Version.Testing.REDIS_5_VERSION
import Build_gradle.Version.Testing.REDIS_6_VERSION
import Build_gradle.Version.Testing.REDIS_7_VERSION
import org.owasp.dependencycheck.gradle.extension.AnalyzerExtension

plugins {
    java
    kotlin("jvm") version "1.7.21"
    id("org.jetbrains.dokka") version "1.7.20"
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.kover") version "0.6.1"
    id("org.owasp.dependencycheck") version "7.3.2"
}

object Version {
    const val JAVA = "11"
    const val KOTLIN_MAJOR = "1.7"

    const val VERTX = "4.3.5"
    const val COROUTINES = "1.6.4"
    const val JACKSON = "2.14.0"

    object Testing {
        const val REDIS_5_VERSION = "5.0.14"
        const val REDIS_6_VERSION = "6.2.4"
        const val REDIS_7_VERSION = "7.0.5"

        const val JUNIT = "5.9.1"
        const val TEST_CONTAINERS = "1.17.6"
        const val TOXI_PROXY = "2.1.7"
        const val KOTEST = "5.5.4"
        const val LOG4J = "2.19.0"
        const val MOCKK = "1.13.2"
    }

    object Build {
        const val KOTLIN_AS_JAVA_PLUGIN = "1.7.10"
        const val JACOCO_TOOLING = "0.8.8"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(platform("io.vertx:vertx-dependencies:${Version.VERTX}"))
    testImplementation(platform("org.apache.logging.log4j:log4j-bom:${Version.Testing.LOG4J}"))
    testImplementation(platform("org.junit:junit-bom:${Version.Testing.JUNIT}"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:${Version.Testing.TEST_CONTAINERS}"))

    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${Version.COROUTINES}")
    api(vertx("core"))
    api(vertx("redis-client"))
    api(vertx("lang-kotlin-coroutines")) {
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
        exclude("org.jetbrains.kotlin", "*")
    }
    api("com.fasterxml.jackson.module:jackson-module-kotlin:${Version.JACKSON}")

    testImplementation(vertx("junit5"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:toxiproxy")
    testImplementation("eu.rekawek.toxiproxy:toxiproxy-java:${Version.Testing.TOXI_PROXY}")
    testImplementation("io.kotest:kotest-assertions-core-jvm:${Version.Testing.KOTEST}")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl")
    testImplementation("org.apache.logging.log4j:log4j-core")
    testImplementation("io.mockk:mockk:${Version.Testing.MOCKK}")
    testImplementation(vertx("lang-kotlin")) {
        exclude("org.jetbrains.kotlin", "*")
    }

    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${Version.Build.KOTLIN_AS_JAVA_PLUGIN}")
}

fun vertx(module: String): String = "io.vertx:vertx-$module"

kover {
    engine.set(kotlinx.kover.api.DefaultIntellijEngine)
    xmlReport{
        onCheck.set(true)
    }
    htmlReport {
        onCheck.set(true)
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0F
    autoUpdate = true
    analyzers(closureOf<AnalyzerExtension> {
        assemblyEnabled = false
    })
    skipConfigurations.addAll(configurations.filter { it.name.contains("dokka") }.map { it.name })
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = Version.JAVA
            apiVersion = Version.KOTLIN_MAJOR
            languageVersion = Version.KOTLIN_MAJOR
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = Version.JAVA
            apiVersion = Version.KOTLIN_MAJOR
            languageVersion = Version.KOTLIN_MAJOR
        }
    }
    compileJava {
        sourceCompatibility = Version.JAVA
        targetCompatibility = Version.JAVA
    }
    compileTestJava {
        sourceCompatibility = Version.JAVA
        targetCompatibility = Version.JAVA
    }

    test {
        basicConfiguration()
        environment( "REDIS_VERSION" to REDIS_5_VERSION)
    }

    val redis6Test by registering(Test::class) {
        basicConfiguration()
        environment( "REDIS_VERSION" to REDIS_6_VERSION)
    }

    val redis7Test by registering(Test::class) {
        basicConfiguration()
        environment( "REDIS_VERSION" to REDIS_7_VERSION)
    }

    build {
        dependsOn.add(dependencyCheckAnalyze)
        dependsOn.add(redis6Test)
        dependsOn.add(redis7Test)
    }
}

fun Test.basicConfiguration() {
    maxParallelForks = 1
    useJUnitPlatform()
    systemProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
}

val publishUsername: String by lazy {
    "${findProperty("ossrhUsername")}"
}
val publishPassword: String by lazy {
    "${findProperty("ossrhPassword")}"
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn.add(tasks.dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from("$buildDir/dokka/javadoc")
}

val publishUrl = if ("$version".endsWith("SNAPSHOT")) {
    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
} else {
    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
}

publishing {
    publications {
        repositories {
            maven {
                name = "ossrh"
                setUrl(publishUrl)
                credentials {
                    username = publishUsername
                    password = publishPassword
                }
            }
        }

        create("Kotlin", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
            pom {
                groupId = groupId
                artifactId = artifactId
                version = project.version.toString()
                packaging = "jar"
                name.set("Vert.x Redis Heimdall client")
                description.set("Redis client based on the official one https://vertx.io/docs/vertx-redis-client/java/. " +
                        "This client will provide some additional features like reconnect capabilities, Event bus events on reconnecting related activities.")
                url.set("https://github.com/wem/vertx-redis-client-heimdall")
                scm {
                    connection.set("scm:https://github.com/wem/vertx-redis-client-heimdall.git")
                    developerConnection.set("scm:https://github.com/wem/vertx-redis-client-heimdall.git")
                    url.set("https://github.com/wem/vertx-redis-client-heimdall")
                }
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://www.opensource.org/licenses/MIT")
                        distribution.set("https://github.com/wem/vertx-redis-client-heimdall")
                    }
                }
                developers {
                    developer {
                        id.set("Michel Werren")
                        name.set("Michel Werren")
                        email.set("michel.werren@source-motion.ch")
                    }
                }
            }
        }
    }
}

signing {
    sign(publishing.publications)
}
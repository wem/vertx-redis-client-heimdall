import java.util.*

plugins {
    java
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.dokka") version "1.4.30"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    `maven-publish`
    signing
}

(System.getProperty("release_version") ?: findProperty("release_version"))?.let { version = it.toString() }

val version_vertx = "3.9.6"
val version_coroutines = "1.4.3"

// Testing libs
val version_junit = "5.7.0"
val version_testcontainers = "1.15.2"
val version_kotest = "4.3.0"
val version_log4j = "2.13.3"
val version_mockk = "1.10.2"

repositories {
    jcenter()
}

dependencyManagement {
    imports {
        mavenBom("io.vertx:vertx-dependencies:$version_vertx")
        mavenBom("org.junit:junit-bom:$version_junit")
        mavenBom("org.testcontainers:testcontainers-bom:$version_testcontainers")
        mavenBom("org.apache.logging.log4j:log4j-bom:$version_log4j")
    }
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$version_coroutines")
    api(vertx("core"))
    api(vertx("redis-client"))
    api(vertx("lang-kotlin-coroutines")) {
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
        exclude("org.jetbrains.kotlin", "*")
    }
    api("com.fasterxml.jackson.module:jackson-module-kotlin:${dependencyManagement.importedProperties["jackson.version"]}")

    testImplementation(vertx("junit5"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:toxiproxy")
    testImplementation("eu.rekawek.toxiproxy:toxiproxy-java:2.1.4")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$version_kotest")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl")
    testImplementation("org.apache.logging.log4j:log4j-core")
    testImplementation("io.mockk:mockk:${version_mockk}")
    testImplementation(vertx("lang-kotlin")) {
        exclude("org.jetbrains.kotlin", "*")
    }

    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:1.4.30")
}

fun vertx(module: String): String = "io.vertx:vertx-$module"

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.4"
            languageVersion = "1.4"
            freeCompilerArgs = listOf("-Xinline-classes")
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.4"
            languageVersion = "1.4"
            freeCompilerArgs = listOf("-Xinline-classes")
        }
    }
    compileJava {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    compileTestJava {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    test {
        systemProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
        useJUnitPlatform()
    }
}

val publishUsername: String by lazy {
    "${findProperty("ossrhUsername")}"
}
val publishPassword: String by lazy {
    "${findProperty("ossrhPassword")}"
}

val publicationName = "vertxRedisHeimdall"

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

        create(publicationName, MavenPublication::class.java) {
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
    sign(publishing.publications[publicationName])
}
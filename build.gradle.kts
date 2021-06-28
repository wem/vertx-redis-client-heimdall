plugins {
    java
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.dokka") version "1.4.30"
    `maven-publish`
    signing
}

object Version {
    const val VERTX = "4.1.0"
    const val COROUTINES = "1.4.3"
    const val JACKSON = "2.11.3"

    object Testing {
        const val JUNIT = "5.7.1"
        const val TEST_CONTAINERS = "1.15.3"
        const val TOXI_PROXY = "2.1.4"
        const val KOTEST = "4.4.3"
        const val LOG4J = "2.13.3"
        const val MOCKK = "1.11.0"
    }

    object Build {
        const val KOTLIN_AS_JAVA_PLUGIN = "1.4.30"
    }
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(platform("io.vertx:vertx-dependencies:${Version.VERTX}"))
    api(platform("org.apache.logging.log4j:log4j-bom:${Version.Testing.LOG4J}"))
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
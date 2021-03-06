import java.util.*

plugins {
    java
    kotlin("jvm") version "1.4.30"
    id("com.jfrog.bintray") version "1.8.5"
    `maven-publish`
}
val version_java = "11"
val version_vertx = "4.0.2"
val version_coroutines = "1.4.2"

// Testing libs
val version_junit = "5.7.0"
val version_testcontainers = "1.15.1"
val version_kotest = "4.4.1"
val version_log4j = "2.13.3"
val version_mockk = "1.10.5"
val jackson_version = "2.11.3"

dependencies {
    implementation(platform("io.vertx:vertx-dependencies:$version_vertx"))
    implementation(platform("org.apache.logging.log4j:log4j-bom:$version_log4j"))
    testImplementation(platform("org.junit:junit-bom:$version_junit"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:$version_testcontainers"))

    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$version_coroutines")
    api(vertx("core"))
    api(vertx("redis-client"))
    api(vertx("lang-kotlin-coroutines")) {
        exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
        exclude("org.jetbrains.kotlin", "*")
    }
    api("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")

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
}

fun vertx(module: String): String = "io.vertx:vertx-$module"

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = version_java
            apiVersion = "1.4"
            languageVersion = "1.4"
            freeCompilerArgs = listOf("-Xinline-classes")
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = version_java
            apiVersion = "1.4"
            languageVersion = "1.4"
            freeCompilerArgs = listOf("-Xinline-classes")
        }
    }
    compileJava {
        sourceCompatibility = version_java
        targetCompatibility = version_java
    }
    compileTestJava {
        sourceCompatibility = version_java
        targetCompatibility = version_java
    }
    test {
        systemProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
        useJUnitPlatform()
    }
}

val bintrayUser: String by lazy {
    "${findProperty("bintray_user")}"
}
val bintrayApiKey: String by lazy {
    "${findProperty("bintray_api_key")}"
}

val publicationName = "vertxRedisHeimdall"

bintray {
    user = bintrayUser
    key = bintrayApiKey
    setPublications(publicationName)

    pkg(closureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
        repo = "maven"
        name = project.name
        userOrg = "michel-werren"
        vcsUrl = "https://github.com/wem/vertx-redis-client-heimdall"
        version(closureOf<com.jfrog.bintray.gradle.BintrayExtension.VersionConfig> {
            name = "${project.version}"
            released = "${Date()}"
        })
        setLicenses("MIT")
    })
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        register(publicationName, MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar.get())
            pom {
                groupId = groupId
                artifactId = artifactId
                version = "${project.version}"
                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("http://www.opensource.org/licenses/MIT")
                        distribution.set("https://github.com/wem/vertx-redis-client-heimdall")
                    }
                }
            }
        }
    }
}

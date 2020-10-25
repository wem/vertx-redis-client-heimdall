plugins {
    java
    kotlin("jvm") version "1.4.10"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
}

val version_vertx = "3.9.4"
val version_coroutines = "1.3.9"

// Testing libs
val version_junit = "5.6.1"
val version_testcontainers = "1.15.0-rc2"
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

    testImplementation(vertx("junit5"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:toxiproxy")
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

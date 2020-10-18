plugins {
    java
    kotlin("jvm") version "1.4.10"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
}

val version_vertx = "3.9.4"

// Testing libs
val version_junit = "5.6.1"
val version_testcontainers = "1.15.0-rc2"
val version_kotest = "4.3.0"
val version_log4j = "2.13.3"

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
    implementation(kotlin("stdlib"))
    implementation(vertx("core"))
    implementation(vertx("redis-client"))
    implementation(vertx("lang-kotlin"))
    implementation(vertx("lang-kotlin-coroutines"))

    testImplementation(vertx("junit5"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:toxiproxy")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$version_kotest")
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl")
    testImplementation("org.apache.logging.log4j:log4j-core")
}

fun vertx(module: String): Any = "io.vertx:vertx-$module"

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.4"
            languageVersion = "1.4"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.4"
            languageVersion = "1.4"
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`

    alias(libs.plugins.com.squareup.wire)
    alias(libs.plugins.shadow.jar)
}

repositories {
    mavenCentral()
    maven { url = uri("https://jcenter.bintray.com") }
    maven { url = uri("https://jitpack.io") }
//    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    api(libs.com.discord4j.discord4j.core)
    //    implementation("com.github.discord4j:discord4j:210116a3c3")
    api(libs.ch.qos.logback.logback.classic)
    api(libs.com.google.code.gson.gson)
    api(libs.commons.net.commons.net)
    api(libs.org.apache.commons.commons.lang3)
    api(libs.org.apache.httpcomponents.client5.httpclient5)
    api(libs.com.fasterxml.jackson.dataformat.jackson.dataformat.xml)
    api(libs.com.fasterxml.jackson.datatype.jackson.datatype.jsr310)
    api(libs.redis.clients.jedis)

    implementation(libs.wire.runtime)
    implementation(libs.net.eewbot.base32768j)

    // SQL Database dependencies
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.jooq)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.caffeine)
    implementation(libs.logstash.logback.encoder)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jsonassert)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

group = "net.teamfruit"
version = "2.9.2"
description = "EEWBot"
java.sourceCompatibility = JavaVersion.VERSION_21

wire {
    java {
    }
}

sourceSets {
    named("main") {
        java {
            srcDir("build/generated/source/wire/main/java")
        }
    }
}

tasks {
    named("jar") {
        enabled = false
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        manifest {
            attributes(mapOf("Main-Class" to "net.teamfruit.eewbot.EEWBot"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

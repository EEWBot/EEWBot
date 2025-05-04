import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`

    alias(libs.plugins.com.squareup.wire)
    alias(libs.plugins.shadow.jar)
}

repositories {
    mavenLocal()
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://jcenter.bintray.com") }
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
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
    implementation(libs.net.eewbot.base65536j)
}

group = "net.teamfruit"
version = "2.8.1"
description = "EEWBot"
java.sourceCompatibility = JavaVersion.VERSION_11

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
        manifest {
            archiveClassifier.set("")
            mergeServiceFiles()
            attributes(mapOf("Main-Class" to "net.teamfruit.eewbot.EEWBot"))
//            minimize {
//                exclude(dependency("ch.qos.logback:logback-classic:.*"))
//                exclude(dependency("com.fasterxml.woodstox:woodstox-core:.*"))
//            }
        }
    }

    build {
        dependsOn(shadowJar)
    }
}


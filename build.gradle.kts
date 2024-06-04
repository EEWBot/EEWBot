import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `java-library`
    alias(libs.plugins.shadow.jar)
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://jcenter.bintray.com")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    api(libs.com.discord4j.discord4j.core)
    api(libs.ch.qos.logback.logback.classic)
    api(libs.com.google.code.gson.gson)
    api(libs.commons.net.commons.net)
    api(libs.org.apache.commons.commons.lang3)
    api(libs.org.apache.httpcomponents.client5.httpclient5)
    api(libs.com.fasterxml.jackson.dataformat.jackson.dataformat.xml)
    api(libs.com.fasterxml.jackson.datatype.jackson.datatype.jsr310)
    api(libs.redis.clients.jedis)
}

group = "net.teamfruit"
version = "2.7.7"
description = "EEWBot"
java.sourceCompatibility = JavaVersion.VERSION_11

tasks {
    named<ShadowJar>("shadowJar") {
        manifest {
            archiveClassifier.set("")
            mergeServiceFiles()
            attributes(mapOf("Main-Class" to "net.teamfruit.eewbot.EEWBot"))
            minimize {
                exclude(dependency("ch.qos.logback:logback-classic:.*"))
                exclude(dependency("com.fasterxml.woodstox:woodstox-core:.*"))
            }
        }
    }
}


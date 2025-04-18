pluginManagement {
    repositories {
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jcenter.bintray.com") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "eewbot"

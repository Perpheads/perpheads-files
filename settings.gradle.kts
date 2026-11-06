pluginManagement {
    val quarkusPluginVersion = providers.gradleProperty("quarkusPluginVersion").get()
    val quarkusPluginId = providers.gradleProperty("quarkusPluginId").get()
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name="perpheads-files"

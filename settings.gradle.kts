pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RecordatorioTareas"
include(":app")
project(":app").projectDir = File(rootDir, "aplicación")

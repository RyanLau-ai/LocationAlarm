pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 高德 Maven 仓库
        maven { url = uri("https://maven.aliyun.com/nexus/content/repositories/releases") }
    }
}

rootProject.name = "LocationAlarm"
include(":app")

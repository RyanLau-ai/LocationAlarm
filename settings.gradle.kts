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
        // 高德 Maven 仓库（阿里云 jcenter 镜像）
        maven { url = uri("https://maven.aliyun.com/repository/jcenter/") }
    }
}

rootProject.name = "LocationAlarm"
include(":app")

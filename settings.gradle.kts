pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 高德开放平台 Maven 仓库
        maven("https://repo1.maven.org/maven2/")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "LocationAlarm"
include(":app")

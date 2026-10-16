@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
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
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// F-Droid doesn't support foojay-resolver plugin
rootProject.name = "MakoffMusic"
include(":app")
include(":innertube")

includeBuild("vendor/innertubex") {
    dependencySubstitution {
        substitute(module("com.github.MetrolistGroup.innertubex:innertubex")).using(project(":"))
        substitute(module("com.github.MetrolistGroup:innertubex")).using(project(":"))
    }
}

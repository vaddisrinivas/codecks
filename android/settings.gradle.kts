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
    }
}

rootProject.name = "Codecks"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:security")
include(":core:testing")
include(":domain:actions")
include(":domain:ai")
include(":domain:automations")
include(":domain:decks")
include(":domain:targets")
include(":data:decks")
include(":data:receipts")
include(":data:targets")
include(":runtime:actions")
include(":transport:ssh")
include(":feature:automations")
include(":feature:connection")
include(":feature:create")
include(":feature:deck")
include(":feature:settings")
include(":feature:trackpad")

project(":core:common").projectDir = file("core/common")
project(":core:designsystem").projectDir = file("core/designsystem")
project(":core:security").projectDir = file("core/security")
project(":core:testing").projectDir = file("core/testing")
project(":domain:actions").projectDir = file("domain/actions")
project(":domain:ai").projectDir = file("domain/ai")
project(":domain:automations").projectDir = file("domain/automations")
project(":domain:decks").projectDir = file("domain/decks")
project(":domain:targets").projectDir = file("domain/targets")
project(":data:decks").projectDir = file("data/decks")
project(":data:receipts").projectDir = file("data/receipts")
project(":data:targets").projectDir = file("data/targets")
project(":runtime:actions").projectDir = file("runtime/actions")
project(":transport:ssh").projectDir = file("transport/ssh")
project(":feature:automations").projectDir = file("feature/automations")
project(":feature:connection").projectDir = file("feature/connection")
project(":feature:create").projectDir = file("feature/create")
project(":feature:deck").projectDir = file("feature/deck")
project(":feature:settings").projectDir = file("feature/settings")
project(":feature:trackpad").projectDir = file("feature/trackpad")

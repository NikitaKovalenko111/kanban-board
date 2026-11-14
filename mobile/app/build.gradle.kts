plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Страница расписания ИСУ УУНиТ. {group} заменяется полным названием группы.
// Значения нужно сверить с сайтом и при необходимости переопределить: -PisuGroup203=...
val isuUrlTemplate = providers.gradleProperty("isuUrlTemplate").orNull
    ?: "https://isu.uust.ru/class_schedule/?group={group}"
val isuGroup203 = providers.gradleProperty("isuGroup203").orNull ?: "203"
val isuGroup204 = providers.gradleProperty("isuGroup204").orNull ?: "204"
// Адрес API читается при каждой сборке: -P имеет приоритет над переменной окружения.
val backendBaseUrl = providers.gradleProperty("backendBaseUrl")
    .orElse(providers.environmentVariable("BACKEND_BASE_URL"))
    .orNull.orEmpty().trim().trimEnd('/')
require(backendBaseUrl.none { it == '"' || it == '\\' || it == '\n' || it == '\r' }) {
    "Некорректный backendBaseUrl"
}

android {
    namespace = "com.kanbanboard.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kanbanboard.mobile"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "0.5.3-demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ISU_URL_TEMPLATE", "\"$isuUrlTemplate\"")
        buildConfigField("String", "ISU_GROUP_203", "\"$isuGroup203\"")
        buildConfigField("String", "ISU_GROUP_204", "\"$isuGroup204\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Две версии приложения, ставятся рядом (у mock свой applicationId и свои данные):
    //  clean — чистый старт: только тимлид и участник, всё остальное создают пользователи;
    //  mock  — заполненная команда, проекты, задачи, события и файлы для показа и тестирования.
    flavorDimensions += "data"
    productFlavors {
        create("clean") {
            dimension = "data"
            isDefault = true
            resValue("string", "app_name", "Код в сапоге")
        }
        create("mock") {
            dimension = "data"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            resValue("string", "app_name", "Код в сапоге (мок)")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

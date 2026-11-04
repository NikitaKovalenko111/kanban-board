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

android {
    namespace = "com.kanbanboard.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kanbanboard.mobile"
        minSdk = 29
        targetSdk = 37
        versionCode = 6
        versionName = "0.5.1-demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ISU_URL_TEMPLATE", "\"$isuUrlTemplate\"")
        buildConfigField("String", "ISU_GROUP_203", "\"$isuGroup203\"")
        buildConfigField("String", "ISU_GROUP_204", "\"$isuGroup204\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
}

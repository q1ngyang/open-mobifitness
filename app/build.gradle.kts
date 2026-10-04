plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.openmobifitness.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.openmobifitness.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 12
        versionName = "0.3.2"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        javaCompileOptions { annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("localRelease") {
            System.getenv("OPENMOBI_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("OPENMOBI_STORE_PASSWORD")
            keyAlias = "openmobi"
            keyPassword = System.getenv("OPENMOBI_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            if (System.getenv("OPENMOBI_KEYSTORE") != null) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets.getByName("test").resources.srcDir("schemas")
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

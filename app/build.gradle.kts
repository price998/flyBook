import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 从 local.properties 读取 API Keys
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        buildConfigField("String", "AI_API_KEY", "\"${properties.getProperty("ai.api.key", "")}\"")
        buildConfigField(
                "String",
                "SERPER_API_KEY",
                "\"${properties.getProperty("serper.api.key", "")}\""
        )
        buildConfigField(
                "String",
                "XUNFEI_APPID",
                "\"${properties.getProperty("xunfei.appid", "")}\""
        )

        // Room Schema 导出配置
        javaCompileOptions {
            annotationProcessorOptions {
                arguments +=
                        mapOf(
                                "room.schemaLocation" to "$projectDir/schemas",
                                "room.incremental" to "true"
                        )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Room components
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Room 测试支持（可选）
    // testImplementation("androidx.room:room-testing:2.6.1")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // ML Kit for OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Markwon for Markdown rendering
    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:syntax-highlight:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:html:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:image:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:ext-tables:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:ext-strikethrough:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:ext-tasklist:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation("io.noties.markwon:linkify:4.6.2") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }

    // Prism4j for syntax highlighting
    implementation("io.noties:prism4j:2.0.0") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    kapt("io.noties:prism4j-bundler:2.0.0")

    // 科大讯飞语音识别SDK
    implementation(files("libs/Msc.jar"))
    
    // SwipeRevealLayout 左滑删除/恢复控件
    implementation("com.chauthai.swipereveallayout:swipe-reveal-layout:1.4.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // WorkManager 后台任务调度
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Fragment KTX 扩展库
    implementation("androidx.fragment:fragment-ktx:1.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.junit)
}

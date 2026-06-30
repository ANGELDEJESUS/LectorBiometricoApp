plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.clariti.lectorbiometricoapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clariti.lectorbiometricoapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- AGREGADO PARA EL PROYECTO BIOMÉTRICO ---

    // 1. Integración del SDK de DigitalPersona (Archivos locales)
    // Esto le dice a Gradle que compile cualquier .jar o .aar que metas en la carpeta app/libs
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // 2. Corrutinas de Kotlin (Crucial para el hardware)
    // El lector bloquea el hilo esperando el dedo. Usaremos corrutinas para evitar que la pantalla se congele.
    // Nota: Las declaro con string directo ya que no tengo acceso a tu archivo libs.versions.toml
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 3. Retrofit y Gson (Para enviar la huella al Microservicio Spring Boot)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}


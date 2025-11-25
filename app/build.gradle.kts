plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services") // ✅ Needed for Firebase
}

android {
    namespace = "com.humangodcvaki.whoi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.humangodcvaki.whoi"
        minSdk = 23
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // ✅ Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.1.0")

    // ✅ Credential Manager (for passkey/future-proof sign-in)
    val credentialManagerVersion = "1.2.0-alpha02"
    implementation("androidx.credentials:credentials:$credentialManagerVersion")
    implementation("androidx.credentials:credentials-play-services-auth:$credentialManagerVersion")

    // ✅ Glide for profile image
    implementation("com.github.bumptech.glide:glide:4.14.2")
    ksp("com.github.bumptech.glide:compiler:4.14.2")

    // ✅ Firebase BOM and Services (Analytics + Auth)
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")


    // Add the dependency for the Cloud Firestore library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-firestore")


    implementation("com.google.firebase:firebase-auth:22.0.0")
    implementation("com.google.firebase:firebase-database:20.3.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.android.billingclient:billing:6.2.0") // Replace with the latest version if needed
    implementation("com.google.firebase:firebase-auth:22.3.1") // latest as of now
    implementation("com.google.firebase:firebase-firestore:24.10.3") // latest as of no
    implementation("androidx.recyclerview:recyclerview:1.3.2") // Or latest version
    implementation("com.android.billingclient:billing-ktx:6.1.0")
    implementation("com.google.android.gms:play-services-ads:22.6.0")



}

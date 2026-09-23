plugins { id("com.android.application") }

android {
    namespace = "com.cinedeck.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cinedeck.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

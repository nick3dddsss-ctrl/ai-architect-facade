plugins { id("com.android.application") }

android {
    namespace = "com.cinedeck.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cinedeck.tv"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

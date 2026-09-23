plugins { id("com.android.application") }
android {
 namespace = "com.cinedeck.tv"
 compileSdk = 36
 defaultConfig { applicationId = "com.cinedeck.tv"; minSdk = 21; targetSdk = 28; versionCode = 13; versionName = "1.1.2-offline" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

plugins { id("com.android.application") }
android {
 namespace = "com.cinedeck.tv"
 compileSdk = 36
 defaultConfig { applicationId = "com.cinedeck.tv"; minSdk = 21; targetSdk = 28; versionCode = 15; versionName = "1.1.4-online" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

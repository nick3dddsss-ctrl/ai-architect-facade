plugins { id("com.android.application") }
android {
 namespace = "com.cinedeck.tv"
 compileSdk = 36
 defaultConfig { applicationId = "com.cinedeck.tv"; minSdk = 21; targetSdk = 28; versionCode = 18; versionName = "1.2.0-player" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation("com.google.android.exoplayer:exoplayer:2.19.1")
}

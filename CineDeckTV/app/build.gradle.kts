plugins { id("com.android.application") }
android {
 namespace = "com.cinedeck.tv"
 compileSdk = 36
 defaultConfig { applicationId = "com.cinedeck.tv"; minSdk = 23; targetSdk = 36; versionCode = 11; versionName = "1.1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation("androidx.annotation:annotation:1.9.1")
 implementation("androidx.media3:media3-exoplayer:1.11.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
 implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
 implementation("androidx.media3:media3-ui:1.11.1")
}

plugins { id("com.android.application") }
android {
    namespace = "io.github.lcblauncher.fixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.lcblauncher.fixture"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

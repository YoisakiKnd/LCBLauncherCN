import java.time.Duration

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.lcblauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.lcblauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "0.2.1-container-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = providers.gradleProperty("probeTestHeap").orElse("256m").get()
            it.maxParallelForks = 1
            val testMetaspace = providers.gradleProperty("probeTestMetaspace").orElse("192m").get()
            it.jvmArgs("-XX:MaxMetaspaceSize=$testMetaspace", "-XX:ActiveProcessorCount=1")
            it.timeout.set(Duration.ofMinutes(5))
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("maven.repo.local", rootProject.file(".tools/robolectric-maven").absolutePath)
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

// Embed only our original test guest. User game APKs are imported on the phone, never shipped.
abstract class StageContainerFixture : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceApk: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction fun stage() {
        val destination = outputDirectory.get().asFile.apply { mkdirs() }
        sourceApk.get().asFile.copyTo(destination.resolve("container-fixture.apk"), overwrite = true)
    }
}
val stageContainerFixture by tasks.registering(StageContainerFixture::class) {
    dependsOn(":container-fixture:assembleDebug")
    sourceApk.set(project(":container-fixture").layout.buildDirectory.file("outputs/apk/debug/container-fixture-debug.apk"))
    outputDirectory.set(layout.buildDirectory.dir("generated/containerFixtureAssets"))
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(stageContainerFixture, StageContainerFixture::outputDirectory)
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

# Third-party components

LCBLauncher is an independent implementation. No source from existing Limbus localization launchers or VirtualApp is bundled. No game APK, translation archive, game artwork, or game font is included.

Build and runtime dependencies retain their respective licenses:

| Component | Purpose | License / upstream |
| --- | --- | --- |
| Kotlin, Kotlin Gradle and Compose compiler plugins | Language and compilation | Apache-2.0, https://github.com/JetBrains/kotlin |
| kotlinx.serialization | Local JSON parsing | Apache-2.0, https://github.com/Kotlin/kotlinx.serialization |
| kotlinx.coroutines | Background operations (AndroidX transitive dependency) | Apache-2.0, https://github.com/Kotlin/kotlinx.coroutines |
| AndroidX Activity, Compose, Lifecycle and transitive AndroidX components | Android UI and lifecycle | Apache-2.0 and component-specific notices, https://android.googlesource.com/platform/frameworks/support/ |
| Android Gradle Plugin | APK building | Apache-2.0, https://android.googlesource.com/platform/tools/base/ |
| Gradle and Gradle Wrapper | Build tooling | Apache-2.0 and bundled notices, https://github.com/gradle/gradle |
| JUnit 4 | Host-side tests only | EPL-1.0, https://github.com/junit-team/junit4 |
| Robolectric | Android host-side tests only | MIT and bundled notices, https://github.com/robolectric/robolectric |

Downloaded JDK and Android SDK installations are local development tools under the ignored `.tools/` directory and are not distributed in the APK. Their upstream licenses and terms apply separately.

Limbus Company and Project Moon names and associated marks belong to their respective rights holders. This unofficial tool is not endorsed by Project Moon. User-selected translations are not relicensed by this project; users must observe their original authors' terms.

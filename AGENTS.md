# Build environment

The shared server previously ran out of resources during Android builds. The user moved all compilation to GitHub Actions.

- Do not start Java, Gradle, Kotlin compilation, Android lint, Robolectric tests, emulators, or Android SDK installation on this server.
- Use `.github/workflows/android.yml` on GitHub-hosted runners for builds and tests. Follow the remote run and fix failures there.
- Local file edits, Git operations, lightweight YAML/shell/Python syntax checks and querying Actions status are allowed.
- `scripts/ci-build.sh` is for GitHub Actions only. Do not bypass its environment guard to run a build locally.
- Never commit `.tools/`, build outputs, local SDK paths, keystores, credentials, or game APKs.
- The current probe has no game translation runtime. Keep the UI and documentation explicit about that boundary.

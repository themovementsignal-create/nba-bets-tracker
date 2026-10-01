# Working on this repo
- Read SPEC.md first. Work one checkbox at a time, and tick it off once it's working.
- I build and test only from my phone via GitHub Actions. Keep main building at all times. Make small, focused commits.
- I'm not an Android developer, but I'm analytical. Explain decisions briefly, in plain language.
- Data safety: every Room schema change needs a proper migration. Never use destructive migration fallbacks. Never change the signing setup or package name.
- Never commit secrets or keystores.
- Ask before adding a major dependency or changing the architecture.
- After each change, tell me exactly what to test on my phone.

## Project facts
- App name: **Agon** (display name only, in `res/values/strings.xml`). Package name / applicationId: `io.github.themovementsignal.training` (permanent; changing it breaks updates and data).
- Look: dark only, near-black + gold accent, bronze highlights; colours and fonts live in `ui/theme/Theme.kt`. Avoid purple.
- Versions live in `gradle/libs.versions.toml`.
- `.github/workflows/build.yml` builds a signed release APK (plus unit tests) on every push and, on `main`, publishes a GitHub Release tagged `build-<run number>`. versionCode = the Actions run number.
- `.github/workflows/ui-tour.yml` runs on every branch except `main`: it boots an emulator, runs `UiTourTest` (clicks through every screen) and force-pushes screenshots, logcat and the result to the `ci-screenshots` branch. Check those screenshots before merging to `main`.
- Signing secrets (repo Settings → Secrets → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Database: Room, schema version 1 shipped in Build 8. Any entity change from now on needs a version bump and a real Migration (see `data/AppDatabase.kt`). CI uploads the generated schema JSON as an artifact.
- Code map: `data/` (entities, DAO, seed), `domain/` (pure logic + unit tests: 1RM, plates, weekly load, Strong CSV), `io/DataIO.kt` (backup/restore/export/import), `timer/` (rest timer service), `ui/` (Compose screens).

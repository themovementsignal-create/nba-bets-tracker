# Bear: working on this repo
- Read SPEC.md first. Work one checkbox at a time, and tick it off once it's working.
- I build and test only from my phone via GitHub Actions. Keep main building at all times. Make small, focused commits.
- I'm not an Android developer, but I'm analytical. Explain decisions briefly, in plain language.
- Data safety: every Room schema change needs a proper migration. Never use destructive migration fallbacks. Never change the signing setup or package name.
- The package name `com.muir.bear` is final and must never change.
- Never commit secrets or keystores.
- Ask before adding a major dependency or changing the architecture.
- After each change, tell me exactly what to test on my phone.

## Project facts
- App name: **Bear**; the launcher label is lowercase `bear` (in `res/values/strings.xml`), matching the wordmark. Package name / applicationId / namespace: `com.muir.bear` (final; changing it makes Android treat it as a different app, losing updates and data).
- Brand: read the brief at the top of SPEC.md before any design or copy decision. Quiet and honest: no gimmicks, streak-shaming or hype.
- Logo: always use the `BearWordmark` composable (`ui/BearWordmark.kt`): lowercase "bear" in Archivo Black with the bronze anvil (`drawable/bear_anvil.xml`) as the full stop, anvil as wide as the "a", on the baseline. Never redraw it by hand.
- Motto: "Durum patientia frango" (`BearMotto`, Cormorant Garamond italic, bronze) sits beneath the wordmark on the Home header and the About screen only.
- Look: dark only. Theme tokens: background #121212, bronze accent #A8875A, text/wordmark #E9E3D7. Colours and fonts live in `ui/theme/Theme.kt`, window colours in `res/values/colors.xml`. Avoid purple.
- Fonts are bundled in `res/font` (Archivo for the UI, Archivo Black for the wordmark, Cormorant Garamond italic for the motto), SIL OFL licences in `/licenses`.
- Icon: adaptive icon in `res/mipmap-anydpi/ic_launcher.xml`: the bronze anvil centred on #121212 well inside the safe zone (`drawable/ic_launcher_foreground.xml`) plus a monochrome layer for themed icons.
- Versions live in `gradle/libs.versions.toml`.
- `.github/workflows/build.yml` builds a signed release APK (plus unit tests) on every push and, on `main`, publishes a GitHub Release tagged `build-<run number>`. versionCode = the Actions run number.
- `.github/workflows/ui-tour.yml` runs on every branch except `main`: it boots an emulator, runs `UiTourTest` (clicks through every screen) and force-pushes screenshots, logcat and the result to the `ci-screenshots` branch. Check those screenshots before merging to `main`.
- Signing secrets (repo Settings → Secrets → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Database: Room. Schema JSON history is committed in `app/schemas/` (v1 shipped in Build 8 under the old package, v2 adds sleep tracking, steps and conditioning kind via AutoMigration). Any entity change needs a version bump, a migration and a `MigrationTest` case. CI publishes the generated schema JSON to the `ci-screenshots` branch.
- Code map (under `app/src/main/java/com/muir/bear/`): `data/` (entities, DAO, seed), `domain/` (pure logic + unit tests: 1RM, plates, weekly load, Strong CSV), `io/DataIO.kt` (backup/restore/export/import), `timer/` (rest timer service), `sleep/` (overnight tracker + smart alarm, YAMNet snore model in `assets/yamnet.tflite`, Apache-2.0), `steps/` (pedometer), `ui/` (Compose screens).

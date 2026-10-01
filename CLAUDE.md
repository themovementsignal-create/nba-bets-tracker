# Working on this repo
- Read SPEC.md first. Work one checkbox at a time, and tick it off once it's working.
- I build and test only from my phone via GitHub Actions. Keep main building at all times. Make small, focused commits.
- I'm not an Android developer, but I'm analytical. Explain decisions briefly, in plain language.
- Data safety: every Room schema change needs a proper migration. Never use destructive migration fallbacks. Never change the signing setup or package name.
- Never commit secrets or keystores.
- Ask before adding a major dependency or changing the architecture.
- After each change, tell me exactly what to test on my phone.

## Project facts
- Package name / applicationId: `io.github.themovementsignal.training` (permanent; changing it breaks updates and data).
- Versions live in `gradle/libs.versions.toml`.
- `.github/workflows/build.yml` builds a signed release APK on every push to `main` and publishes it as a GitHub Release tagged `build-<run number>`. versionCode = the Actions run number.
- Signing secrets (repo Settings → Secrets → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

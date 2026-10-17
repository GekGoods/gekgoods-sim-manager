# GekGoods SIM Manager release rules

## Google Play Android release

- Build the unprivileged app module (`:app-unpriv`) for ordinary, non-rooted
  phones. Do not upload the privileged `:app` module's bundle.
- Keep the Play package name `com.gekgoods.simmanager`.
- Pass only the four-part numeric version to Gradle, for example
  `gekgoodsReleaseVersion=1.7.2.2`. Gradle formats the visible version name as
  `gekgoods-v1.7.2.2`; do not add the `gekgoods-v` prefix to the property.
- Set `gekgoodsReleaseVersionCode` to the next integer not already used in
  Play Console. Version code and visible version name are separate values.
- Keep signing credentials and keystores local and ignored by Git. Do not
  commit secrets or generated APK/AAB files.
- Before uploading, verify the AAB package name, version code, exact visible
  version name, and signature. The visible version must begin with
  `gekgoods-v` and must not include branch names, commit hashes, or `-dirty`.

## Windows release build

Set the project properties for the release, then run:

```powershell
$env:ORG_GRADLE_PROJECT_gekgoodsReleaseVersion = '1.7.2.2'
$env:ORG_GRADLE_PROJECT_gekgoodsReleaseVersionCode = '822'
.\gradlew.bat --no-daemon --console=plain :app-unpriv:bundleRelease
```

For later releases, replace the example version and choose an unused version
code. Do not commit the generated AAB unless the repository's release process
explicitly requires storing release artifacts.

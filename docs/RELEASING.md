# Releasing and signing

## Pre-releases (alpha / beta)
Push a tag that contains a dash, such as `v0.1.0-alpha`. CI creates a GitHub
**pre-release** with:
- `app-full-arm64-v8a-debug.apk`
- `app-full-universal-debug.apk`
- `zakdroid-proot-source.tar.gz` (GPL source of the bundled PRoot/talloc)
- `SHA256SUMS.txt`

Debug APKs are signed with the CI runner's temporary debug key. That key changes
on every run, so installing a newer alpha can require uninstalling the old one.

## Final releases
Push a tag without a dash (e.g. `v0.1.0`). CI attaches the **release** APKs
(R8-minified), signed when the four secrets below exist. If any secret is
missing, signing is skipped, CI stays green, and the APKs are named `*-unsigned.apk`.

## Create the release keystore (once, on your Debian machine)

Never commit the keystore, and never paste it into an issue or chat.

```bash
sudo apt-get install -y openjdk-21-jdk-headless        # provides keytool

mkdir -p ~/zakdroid-keys && chmod 700 ~/zakdroid-keys
cd ~/zakdroid-keys

keytool -genkeypair -v \
  -keystore zakdroid-release.jks \
  -storetype PKCS12 \
  -alias zakdroid \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=zakomi TECH, O=zakomi TECH, C=MR"
# keytool asks for the keystore password. With PKCS12 the key password is the same
# value; use it for both KEYSTORE_PASSWORD and KEY_PASSWORD.

# Check it
keytool -list -v -keystore zakdroid-release.jks -alias zakdroid | head -20

# Base64 without line breaks, for the GitHub secret
base64 -w 0 zakdroid-release.jks > zakdroid-release.jks.b64
```

## Add the secrets on GitHub
1. Open https://github.com/studyzakomi-oss/ZAKDROID-CODE → **Settings** →
   **Secrets and variables** → **Actions** → **New repository secret**.
2. Add four secrets:

   | Name | Value |
   |---|---|
   | `KEYSTORE_BASE64` | full contents of `zakdroid-release.jks.b64` (`cat zakdroid-release.jks.b64`, copy the single line) |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | `zakdroid` |
   | `KEY_PASSWORD` | the key password (same as the keystore password for PKCS12) |
3. Delete the `.b64` file afterwards: `shred -u ~/zakdroid-keys/zakdroid-release.jks.b64`.
4. The next CI run's "Report release signature" step should show `signed:` for each release APK.

## ⚠️ Back up the keystore
Android only installs an update when it is signed with **the same key** as the
installed app. **If you lose `zakdroid-release.jks` or its password, nobody can ever
update installed copies of zakdroid-code**; users would have to uninstall (losing
their data) and install a differently signed app. F-Droid and other stores also
tie the app to this key.

- Keep at least two offline copies, e.g. an encrypted USB drive plus a password
  manager attachment, in different places.
- Store the password separately from the file.
- Test a restored copy once with `keytool -list -keystore <copy>`.
- If the key ever leaks, anyone could ship updates that look like yours. Treat it
  like a master password.

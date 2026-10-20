# Dolphin Dualscreen

A personal build of [Dolphin](https://github.com/dolphin-emu/dolphin) for the AYN Thor that uses the
Thor's bottom screen as a companion display. The first target is Luigi's Mansion: a live mansion
map with Luigi's current room, plus health, Boos captured and keys.

It installs as its own app ("Dolphin Dualscreen", `io.github.sirbilliams.dolphindualscreen`), so it
sits alongside regular Dolphin with separate settings.

## Branches

- `upstream` — unmodified Dolphin. Each commit is a snapshot import of `dolphin-emu/dolphin`
  master (the upstream commit hash is in the message). Nothing else is committed here.
- `main` — our work, built on top of `upstream`.

### Updating Dolphin

1. Import the new upstream snapshot as the next commit on `upstream`:

   ```bash
   git clone --depth 1 https://github.com/dolphin-emu/dolphin.git /tmp/dolphin-up
   UP=$(git -C /tmp/dolphin-up rev-parse HEAD)
   git -C /tmp/dolphin-up fetch https://github.com/sirbilliams/Dolphin-Dualscreen.git upstream
   C=$(git -C /tmp/dolphin-up commit-tree "$UP^{tree}" -p FETCH_HEAD \
         -m "Import dolphin-emu/dolphin @ ${UP:0:10}" -m "Upstream-Commit: $UP")
   git -C /tmp/dolphin-up push https://github.com/sirbilliams/Dolphin-Dualscreen.git "$C:refs/heads/upstream"
   ```

2. Merge it: `git checkout main && git pull && git merge origin/upstream`, fix any conflicts, push.

## Builds

Every push to `main` builds a signed arm64 APK with GitHub Actions
(`.github/workflows/android.yml`). Download it from the run's **Artifacts** section and install
it over the previous build on the Thor; settings and saves are kept.

Signing uses the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. Keep a backup of the keystore: without it, a new build can't be installed over an
old one.

Local release build (arm64 only, same as CI):

```bash
cd Source/Android
./gradlew assembleRelease -Pabis=arm64-v8a -PskipNativeTests \
  -Pkeystore=/path/to/dolphin-thor.jks -Pstorepass=... -Pkeyalias=dolphinthor -Pkeypass=...
```

## Game files and art

No game files, ISOs or Nintendo artwork go in this repository. Anything the companion screen
needs from the game is loaded from a folder on the device.

## Credits

- Luigi's Mansion memory addresses: the Luigi's Mansion Archipelago client
  ([BootsinSoots/Archipelago](https://github.com/BootsinSoots/Archipelago), MIT).
- Room layout reference: [DevinLB/Luigis_Mansion_Poptracker](https://github.com/DevinLB/Luigis_Mansion_Poptracker) (MIT).
- Dolphin is licensed GPLv2+; see `COPYING`.

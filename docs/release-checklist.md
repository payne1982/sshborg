# Releasing

What a release actually involves here, in the order it happens, and why the odd-looking steps
exist. Every one of them is here because it was forgotten once.

Two rules stand above the list: **nothing is pushed or uploaded without asking**, and a step that
was skipped is said out loud rather than left to be discovered on a store.

## 1. Before the train

- [ ] Everything to ship is merged into `V1_DEV` and has been tried on the phone by the person
      releasing. Tests do not replace that; they only keep what was already checked from breaking.
- [ ] `./gradlew test` — green. It is also wired into `bundleRelease`, so a red test stops the
      release build by itself. Point it at the corpora to run everything:
      `./gradlew test -PkeyCorpus=… -PeditorCorpus=…` (see `test.properties.example`).
- [ ] `scripts/run-instrumented.sh` — green on the emulator. Run it on the **TV** AVD too when the
      release touches anything on screen: the D-pad walk only means something there, and Play has
      rejected this app twice over television details.
- [ ] Release notes written: `release_notes/release_notes_v<code>.txt` and the fastlane changelogs.
      `scripts/check-notes.sh <code>` checks every locale against Play's 500-character limit and
      that the two copies say the same thing. The limit is per locale and German and French are
      always the tightest.
- [ ] If a new library ships in this release, build it minified and try the feature it belongs to:
      R8 runs over it and nothing guarantees it survives. A one-off `applicationIdSuffix = ".r8test"`
      installs that build beside both the debug app and the store one — the locally signed release
      APK cannot install over the Play version, which Google re-signs. Never commit that patch.

## 2. The train

- [ ] Bump `versionCode` in `app/build.gradle.kts`; `versionName` only when it is a feature release.
- [ ] Commit the bump on `V1_DEV`.
- [ ] `git checkout V1 && git merge --no-ff V1_DEV`.
- [ ] `git tag v<version>` on the merge.
- [ ] `./gradlew clean bundleRelease lintVitalRelease`. **`clean` whenever a dependency, the
      compile SDK or the toolchain changed** — an incremental build over a dependency change has
      produced artefacts that do not match their sources.
- [ ] `git diff --stat v<version>..HEAD -- app/` must be empty before the AAB is handed over.
      Anything else may move after the tag (the site does), but the app must be what was tagged.

## 3. Publishing — only with permission

- [ ] Push `V1_DEV`, `V1` and the tag. F-Droid builds from the tag by itself; it uses
      `assembleRelease`, which is why the tests are not wired to that task.
- [ ] Upload the AAB to Play (test track first) and to Huawei AppGallery.
- [ ] Rebuild the site **after** the tag exists — `cd site && node build.js` — because the changelog
      pages list a version only once it is tagged. Commit the regenerated pages and deploy.
- [ ] Expect Play to take days rather than minutes, and longer still when the release touches
      anything about security.

## 4. Once it is approved

- [ ] Ko-fi post (short lead-in, three bullets, an "Also:" line; English only).
- [ ] Reply to the issues this release answers.
- [ ] Update the memory: what shipped, what is still owed.

## Things that have gone wrong before

- The **Android TV pass** was owed for three releases in a row, because doing it by hand means
  holding a key in front of a television. It is now `scripts/run-instrumented.sh` on the TV AVD.
- A **migration** was added and then removed, leaving a database newer than its schema on a phone
  that had already run it. Debug builds set `allowBackup="true"`, so Android's own backup brings
  such a database back after a reinstall; clearing the app's data is the only way out.
- The **500-character limit** is per locale, and a bullet that fits in English does not fit in
  German. `scripts/check-notes.sh` now says so before an upload does.
- A screenshot taken on the TV emulator would show the hosts somebody left on it, with their real
  addresses. The screenshot run refuses to start if the app on that device already holds any.

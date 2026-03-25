# Privacy Policy for SSHBorg

*Last updated: March 24, 2026*

## Overview

SSHBorg ("the app") is an SSH/SFTP client for Android. This privacy policy explains what data the app handles, how it is stored, and your rights as a user.

## Data We Collect and Store

SSHBorg stores all data **locally on your device only**. We do not operate any servers, and no data is ever transmitted to us or any third party.

The following data is stored locally:

- **Host configurations** — hostname, port, username, and optionally a saved password for SSH servers you add to the app.
- **SSH private keys** — cryptographic keys you generate or import within the app.
- **App preferences** — settings such as theme, language, biometric lock timeout, and display options.

## How Data Is Protected

- SSH private keys and saved passwords can optionally be encrypted using the **Android Keystore**, a hardware-backed secure storage system provided by your device. When enabled, encrypted data is bound to your device and app installation.
- The app can optionally require **biometric authentication** (fingerprint, face, or device PIN) before granting access.
- The app prevents screenshots and screen recording via the `FLAG_SECURE` flag.

## Network Connections

SSHBorg connects exclusively to SSH servers that **you explicitly configure**. The app makes no connections to any external services, analytics platforms, advertising networks, or our own servers.

All network traffic is SSH protocol traffic between your device and your own servers.

## Data Sharing

We do not share, sell, or transmit your data to any third party. Ever.

## Permissions

The app requests the following Android permissions:

- **INTERNET** — required to establish SSH/SFTP connections to your servers.
- **USE_BIOMETRIC / USE_FINGERPRINT** — optional, used only if you enable biometric lock.
- **FOREGROUND_SERVICE** — used to show a persistent notification while SSH sessions are active.

## Data Deletion

All app data can be deleted at any time by uninstalling the app or clearing app data from Android Settings. There is no account or server-side data to delete.

**Note:** SSH private keys encrypted with the Android Keystore are permanently inaccessible after uninstalling the app. Export your public keys to your servers before uninstalling if needed.

## Children's Privacy

SSHBorg is not directed at children under 13 and does not knowingly collect any information from children.

## Changes to This Policy

If this policy changes, the updated version will be published at the same URL with a new "Last updated" date.

## Contact

If you have questions about this privacy policy, you can contact us at: *massimiliano.playdev@gmail.com*

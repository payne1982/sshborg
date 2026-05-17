# SSHBorg

An SSH client for Android. Full terminal emulation, SFTP file manager, SSH key management, jump hosts, agent forwarding, and biometric lock — with no ads, no tracking, and no cloud.

[![Get it on Google Play](https://img.shields.io/badge/Google-Play-414141?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.sshborg) &nbsp; [![Explore it on AppGallery](https://img.shields.io/badge/Huawei-AppGallery-CF0A2C?style=for-the-badge&logo=huawei&logoColor=white)](https://appgallery.huawei.com/app/C117647135)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## Features

- **SSH terminal** — VT100/xterm emulation, full UTF-8, multiple concurrent sessions
- **SFTP file manager** — browse, upload, download, rename, and delete files
- **SSH key auth** — generate Ed25519, ECDSA, and RSA keys directly on device
- **Jump host support** — connect through one or more bastion hosts with transparent tunnelling
- **SSH agent forwarding** — forward your keys through jump chains
- **Biometric lock** — protect access with fingerprint or face unlock
- **Backup / restore** — export and import host configurations as JSON (credentials excluded)
- **No ads, no tracking, no third-party SDKs**

Requires Android 10 (API 29) or later.

## Building

```bash
# Clone the repo
git clone https://github.com/payne1982/sshborg.git
cd sshborg

# Build a debug APK
JAVA_HOME=/path/to/jdk21 ./gradlew assembleDebug
```

A release build additionally requires signing configuration in `local.properties` (not tracked):

```
signing.storeFile=/path/to/keystore.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
```

## Dependencies

- [JSch (mwiede fork)](https://github.com/mwiede/jsch) — SSH protocol implementation
- [Bouncy Castle](https://www.bouncycastle.org/) — cryptography
- [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/compose) — UI framework
- [Room](https://developer.android.com/training/data-storage/room) — local database

## License

SSHBorg is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License v3.0](LICENSE).

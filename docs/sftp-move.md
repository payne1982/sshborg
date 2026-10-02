# Moving a file on the server, over SFTP

Written for both SSHBorg apps. The Android app implements what is below; the iOS app uses a
different SFTP library whose behaviour differs in exactly the place that matters, so the rule at
the end is the one that makes the two agree.

Nothing here needs a shell. Every step is an SFTP request, performed by the server on its own
filesystem, with no bytes crossing the network — a move of a 10 GB file is as quick as a move of
an empty one.

## What the protocol offers, and what it does not

**Move: `SSH_FXP_RENAME`.** One request, instant. Two limits that belong to the filesystem and not
to SFTP:

- It works **within one filesystem**. From `/home/you/x` to `/mnt/disk/x` the server fails, the
  same way `mv` has to fall back to copying across mounts.
- POSIX `rename(2)` replaces an existing **directory** only when that directory is **empty**;
  otherwise it is `ENOTEMPTY`. **Two directories are never merged** — not by SFTP, and not by `mv`
  either, which answers "Directory not empty". Merging is a copy, which means `rsync` or `cp -r`.

**Copy: there is none.** No version of SFTP that servers actually speak has a copy request.
OpenSSH 9.0 added `copy-data@openssh.com` and its own `sftp cp` uses it, but neither of the
libraries below exposes it, so a copy means reading the bytes down to the phone and writing them
back up — twice over the mobile link. That is why the apps implement move first and separately.

**Hard links are not a copy.** `hardlink@openssh.com` is instant and server-side, and both
libraries expose it, so it looks like the answer. It is not: a hard link is the same inode under a
second name, so editing the "copy" edits the original, and it only works inside one filesystem.

## The one divergence: a destination that already exists

This is the whole reason this document exists.

**SFTP v3** says of `SSH_FXP_RENAME`: *it is an error if there already exists a file with the name
specified by newpath*. **POSIX `rename(2)`** says the opposite: it replaces the destination
atomically. OpenSSH bridges the two with the `posix-rename@openssh.com` extension, and every
modern OpenSSH advertises it.

What each client does with that:

| | Android — mwiede/jsch 2.28.7 | iOS — libssh2 1.11.1 |
|---|---|---|
| call | `ChannelSftp.rename()` | `libssh2_sftp_rename_ex()` |
| posix-rename | **used automatically** when the server advertises it (`sendRENAME` branches on `extension_posix_rename`) | **not used** by this call; libssh2 has it as a separate function, `libssh2_sftp_posix_rename_ex()` |
| SFTP version | 3, with the extension on top | 3 only (`#define LIBSSH2_SFTP_VERSION 3`) |
| rename flags | not applicable | `LIBSSH2_SFTP_RENAME_OVERWRITE` and friends are **only sent from v5 up** (`if(sftp->version >= 5)`), so passing them at v3 does nothing |
| destination exists | **silently overwritten** on any OpenSSH server | **fails** |

So the same gesture, on the same server, destroys a file in one app and is refused in the other.
Neither is the behaviour to ship, and an app cannot tell which it will get without inspecting the
server's extension list.

## The rule

**Never issue a rename whose destination already exists.**

Check first, with `lstat` — not `stat`: a symbolic link whose target is gone still occupies its
name, and `stat` would report the name as free. Then:

- **Nothing in the way** → one rename. Identical on both clients.
- **A file in the way** → ask the user. "Keep both" renames the incoming file (`name(1)`,
  `name(2)`, …, the first free one). "Overwrite" is implemented by the app, in three steps, never
  by the server:

  1. rename the existing destination to a free parking name;
  2. rename the source onto the destination;
  3. delete the parked file.

  If step 2 fails — a different filesystem, a permission — step 1 is undone and the user's file is
  back where it was. Every rename in that sequence has a free destination, which is what makes it
  behave the same under both libraries, and at no point does the data exist under no name at all.
- **A folder in the way** → offer "keep both" or nothing. **Do not offer to replace it**: the
  server can only do that for an empty directory, so the same button would sometimes replace a
  folder and sometimes refuse, depending on something the user cannot see. Say that two folders
  cannot be merged — that is the system's limit, reported, not ours confessed.

Two more checks worth making before the rename, because the server's answer to both is a bare
failure:

- **A folder cannot be moved into itself or its own subtree.** A prefix comparison of the paths.
- **Across filesystems the rename fails** with nothing more specific than "failure". Report it as
  a failed move rather than translating a guess; the cause is usually this or a permission.

## Where this lives

- Android: `SftpViewModel.paste()`, `replace()` and `freeName()`; `SftpSession.lstatOrNull()`;
  `SftpSession.writeFile()` already relied on the same asymmetry when saving from the editor.
- iOS: `SFTPSession.rename(from:to:)` — note that the three flags it passes are inert at v3, so the
  name `OVERWRITE` in that call promises something that never happens.

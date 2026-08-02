'use strict';
module.exports = {
  // ── index.html ─────────────────────────────────────────────────────────────
  page_title:        'SSHBorg – SSH & SFTP Client for Android',
  meta_description:  'SSHBorg is a powerful SSH and SFTP client for Android. Manage your servers securely from your phone with key authentication, encrypted storage, and biometric lock.',

  nav_features:  'Features',
  nav_security:  'Security',
  nav_guide:     'User Guide',
  nav_support:   'Support',
  nav_privacy:   'Privacy Policy',
  nav_tip:       'Leave a Tip',
  nav_contact:   'Contact',

  hero_sub: 'A powerful SSH &amp; SFTP client for Android.<br>Manage your servers securely, directly from your phone.',

  badge_no_ads:      'No ads',
  badge_no_tracking: 'No tracking',
  badge_no_cloud:    'No cloud',
  badge_free:        'Free',
  badge_android:     'Android 10+',

  cta_download:   'DOWNLOAD',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',

  features_title: '// SYSTEM CAPABILITIES',

  feat_terminal_title: 'FULL SSH TERMINAL',
  feat_terminal_desc:  'Interactive terminal with VT100/xterm emulation, full UTF-8 support, and multiple concurrent sessions.',
  feat_sftp_title:     'SFTP FILE MANAGER',
  feat_sftp_desc:      'Browse, upload, download, rename, and delete files on your servers with an intuitive file manager.',
  feat_keys_title:     'SSH KEY AUTH',
  feat_keys_desc:      'Generate Ed25519, ECDSA, and RSA keys directly on your device. No passwords needed.',
  feat_jump_title:     'JUMP HOST SUPPORT',
  feat_jump_desc:      'Connect through one or more bastion hosts with transparent tunnelling. Full SSH agent forwarding.',
  feat_biometric_title:'APP LOCK',
  feat_biometric_desc: 'Lock the app with biometrics or your device PIN — works even on Android TV without a fingerprint sensor. Configurable timeout.',
  feat_multilingual_title: 'MULTILINGUAL',
  feat_multilingual_desc:  'Available in English, Italian, French, German, Spanish, Portuguese, Ukrainian, Russian, Chinese, and Japanese.',
  feat_theme_title:    'DARK &amp; LIGHT THEME',
  feat_theme_desc:     'Follows the system theme or let you choose. Fully readable in any lighting condition.',
  feat_sessions_title: 'MULTIPLE SESSIONS',
  feat_sessions_desc:  'Keep several SSH and SFTP sessions open simultaneously. Switch between them instantly.',

  security_title: '// PRIVACY FIRST, BY DESIGN',
  security_desc:  'SSHBorg never collects your data. Everything stays on your device — your credentials, your keys, your connections.',

  sec_badge_keystore:   'Android Keystore encryption',
  sec_badge_analytics:  'No analytics',
  sec_badge_sdks:       'No third-party SDKs',
  sec_badge_screenshots:'Screenshot protection',
  sec_badge_opensource: 'Open source — GPL v3',

  security_pp_link: 'Read the full Privacy Policy &rarr;',

  tip_title:  '// LEAVE A TIP',
  tip_desc:   'SSHBorg is free, with no ads and no tracking. If it saves you time, a small tip keeps it going.',
  kofi_cta:        'Support me on Ko-fi',
  kofi_hero_cta:   'Leave me a tip',

  footer_privacy: 'Privacy Policy',
  footer_issues:  'Issues &amp; Feedback',
  footer_source:  'Source Code',
  footer_powered: 'SSH connectivity powered by',

  // ── docs.html ──────────────────────────────────────────────────────────────
  page_title_docs:       'SSHBorg – User Guide',
  meta_description_docs: 'SSHBorg user guide: SSH keys, jump hosts, agent forwarding, command suggestions, tmux, and more.',

  nav_home:          'Home',
  nav_getting_started:'Getting Started',
  nav_ssh_keys:      'SSH Keys',
  nav_jump_hosts:    'Jump Hosts',
  nav_sftp:          'SFTP',
  nav_backup:        'Backup',

  doc_page_title:    '// USER GUIDE',
  doc_page_subtitle: 'Operational guide — what to do, step by step, to get the most out of SSHBorg.',

  toc_title: '// CONTENTS',

  doc_toc: `            <li><a href="#adding-host">Adding a Host</a></li>
            <li><a href="#host-groups">Host Groups</a></li>
            <li><a href="#sftp">SFTP File Manager</a></li>
            <li class="sub"><a href="#sftp">Navigation</a></li>
            <li class="sub"><a href="#sftp">Upload &amp; download</a></li>
            <li class="sub"><a href="#sftp">Multi-select</a></li>
            <li><a href="#ssh-keys">SSH Keys</a></li>
            <li class="sub"><a href="#ssh-keys">Generating a key</a></li>
            <li class="sub"><a href="#ssh-keys">Authorizing on server</a></li>
            <li class="sub"><a href="#ssh-keys">Key security</a></li>
            <li><a href="#suggestions">Command Suggestions</a></li>
            <li class="sub"><a href="#suggestions">How it works</a></li>
            <li class="sub"><a href="#suggestions">Troubleshooting</a></li>
            <li><a href="#terminal">Terminal Gestures</a></li>
            <li><a href="#terminal-settings">Terminal Settings</a></li>
            <li><a href="#extra-keys">Extra Key Bar</a></li>
            <li><a href="#agent-forwarding">Agent Forwarding</a></li>
            <li><a href="#legacy-ciphers">Legacy Ciphers</a></li>
            <li><a href="#connection-drops">Connection Drops</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">Jump Hosts</a></li>
            <li class="sub"><a href="#jump-hosts">Multi-hop chains</a></li>
            <li><a href="#sessions">Multiple Sessions</a></li>
            <li><a href="#security">App Security</a></li>
            <li><a href="#backup">Configuration Backup</a></li>`,

  doc_adding_host: `
            <h2>// ADDING A HOST</h2>
            <p>Tap the <strong>+</strong> button on the hosts screen to add a new server.</p>
            <h3>Required fields</h3>
            <ul>
                <li><strong>Hostname / IP</strong> — the server address or IP. Both IPv4 and IPv6 are supported.</li>
                <li><strong>Port</strong> — defaults to 22. Change it if your server runs SSH on a different port.</li>
                <li><strong>Username</strong> — the Unix user you want to log in as (e.g. <code>ubuntu</code>, <code>root</code>, <code>deploy</code>).</li>
                <li><strong>Authentication</strong> — choose between password or SSH key (recommended).</li>
            </ul>
            <h3>Host fingerprint verification</h3>
            <p>On the first connection SSHBorg shows you the server's fingerprint and asks you to accept it. This is a security check: it ensures you are connecting to the right machine and not to an impostor. You should verify the fingerprint matches the one shown by your server admin or obtained via a trusted channel before accepting.</p>
            <p>Once accepted, the fingerprint is stored locally. If it changes on a future connection, SSHBorg will warn you — this could indicate a server rebuild, a key rotation, or a man-in-the-middle attack.</p>
            <div class="callout callout-info">
                <div class="callout-label">// TIP</div>
                You can check the server fingerprint at any time with:
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_host_groups: `
            <h2>// HOST GROUPS</h2>
            <p>When the server list grows, you can organize hosts into collapsible, color-coded groups — for example <em>Production</em>, <em>Home lab</em>, <em>Clients</em>.</p>
            <h3>Creating a group</h3>
            <ol class="steps">
                <li>Add or edit a host and open the <strong>Group</strong> dropdown.</li>
                <li>Choose <strong>New group…</strong>, type a name and pick one of the predefined colors.</li>
                <li>Save the host — the host list now shows the group as its own section.</li>
            </ol>
            <h3>Working with groups</h3>
            <ul>
                <li><strong>Collapse / expand</strong> — tap a group header to fold or reopen it. The state is remembered, even after restarting the app.</li>
                <li><strong>Color</strong> — the group color appears as a dot in the header and as the tint of the host icons inside the group.</li>
                <li><strong>Edit</strong> — long-press the group header and choose <em>Edit</em> to rename the group or change its color.</li>
                <li><strong>Delete</strong> — long-press the header and choose <em>Delete</em>. The hosts in the group are <em>not</em> deleted — they simply become ungrouped.</li>
            </ul>
            <p>Colors can be picked from the quick swatches or mixed freely with the gradient color picker. A host can also have its <strong>own color</strong> — set it in the host editor, right below the group — which overrides the group color and works for ungrouped hosts too.</p>
            <p>Hosts without a group stay at the top of the list, and if you never create a group the list looks and behaves exactly as before.</p>`,

  doc_sftp: `
            <h2>// SFTP FILE MANAGER</h2>
            <p>The SFTP file manager lets you browse, upload, download, rename, and delete files on your server directly from your phone. Open an SFTP session from the hosts screen by tapping <strong>SFTP</strong>.</p>
            <h3>Navigation</h3>
            <p>Tap a folder to open it. Use the back arrow or tap any segment of the path bar to jump up the directory tree.</p>
            <p>Symbolic links are shown with a small link icon badge. Tapping a symlink navigates to its target: if it points to a directory you enter it, if it points to a file it behaves like a regular file.</p>
            <h3>Uploading files</h3>
            <p>Tap the <strong>upload</strong> button (↑) to pick one or more files from your phone's storage. The upload starts immediately and progress is shown at the top of the screen.</p>
            <h3>Downloading files and folders</h3>
            <p>Tap any file to download it immediately. To download an entire folder, tap the <strong>download</strong> icon next to it — SSHBorg will download the entire directory tree and save it under the <strong>Downloads</strong> folder on your phone.</p>
            <p>If a file already exists at the destination, a dialog will ask you whether to <strong>overwrite</strong>, <strong>skip</strong> the file, or <strong>cancel</strong> the entire transfer.</p>
            <div class="callout callout-info">
                <div class="callout-label">// NOTE ON SYMLINKS</div>
                During a folder download, symbolic links that point to directories are skipped — only regular files (including symlinks to files) are downloaded. This prevents unintended recursive downloads.
            </div>
            <h3>Multi-select and batch operations</h3>
            <p>Long-press any item to enter selection mode, then tap additional items to build up your selection. The toolbar shows actions for the current selection:</p>
            <ul>
                <li><strong>Download</strong> — downloads all selected files and folders at once, with a progress dialog and cancellation support.</li>
                <li><strong>Delete</strong> — deletes all selected items. Deleting a non-empty folder removes all its contents recursively. <em>There is no undo.</em></li>
            </ul>`,

  doc_ssh_keys: `
            <h2>// SSH KEYS</h2>
            <p>Key-based authentication is more secure than passwords and does not require you to remember or type anything after setup.</p>
            <h3>Generating a key</h3>
            <p>Go to <strong>Settings → SSH Keys → Generate new key</strong>. SSHBorg supports:</p>
            <ul>
                <li><strong>Ed25519</strong> — recommended. Fast, compact, and secure.</li>
                <li><strong>ECDSA (P-256 / P-384)</strong> — good compatibility with older servers.</li>
                <li><strong>RSA (2048 / 4096 bit)</strong> — maximum compatibility, but slower.</li>
            </ul>
            <p>Give the key a meaningful name (e.g. <em>my-vps</em> or <em>work-server</em>) so you can identify it later.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// SECURITY NOTE</div>
                SSHBorg intentionally does not allow exporting private keys. The key never leaves the device. If you need the same key on another device, generate a new key on that device and authorize it separately on your servers — this is the safer approach.
            </div>
            <h3>Authorizing the key on the server</h3>
            <p>After generating a key, tap it to see the <strong>public key</strong>. Copy it and paste it into the server's <code>~/.ssh/authorized_keys</code> file for the user you want to log in as.</p>
            <ol class="steps">
                <li>On your phone, open SSHBorg → Settings → SSH Keys → tap the key → copy the public key.</li>
                <li>Log into your server (with a password, or another key you already have).</li>
                <li>Append the public key to the authorized keys file:
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...yourcopiedkey..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>Try connecting with SSHBorg — it should log in without asking for a password.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// SERVER REQUIREMENT</div>
                Make sure your server has <code>PubkeyAuthentication yes</code> in <code>/etc/ssh/sshd_config</code>. This is the default on most distributions, but some hardened images disable it.
            </div>
            <h3>Additional key encryption</h3>
            <p>SSHBorg offers an optional <strong>additional passphrase</strong> for your keys (Settings → SSH Keys → tap a key → Enable encryption). When enabled, the key is encrypted with a passphrase that SSHBorg does not store — you will be asked to enter it each time the key is used.</p>
            <p>This is strongly recommended if you store sensitive server credentials on your phone, or if you don't have an app lock enabled.</p>`,

  doc_suggestions: `
            <h2>// COMMAND SUGGESTIONS</h2>
            <p>SSHBorg shows a suggestion bar above the keyboard while you type in the terminal. Suggestions are drawn from the shell history of the user you connected as.</p>
            <h3>How it works</h3>
            <p>When a terminal session opens, SSHBorg reads the shell history file from the remote server. It tries the following locations in order:</p>
            <ol>
                <li><code>~/.bash_history</code> — default for Bash shells</li>
                <li><code>~/.zsh_history</code> — default for Zsh (also checked as <code>$HISTFILE</code> if set)</li>
                <li><code>~/.local/share/fish/fish_history</code> — for Fish shell users</li>
            </ol>
            <p>The first file that exists and is readable is used. As you type, commands are filtered in real time and shown as chips in the suggestion bar. Tap a chip to insert the command.</p>
            <h3>Troubleshooting</h3>
            <p><strong>No suggestions appear</strong></p>
            <ul>
                <li>The history file may not exist yet (first login, or shell not configured to save history).</li>
                <li>Make sure your shell is configured to write history. For Bash, add to <code>~/.bashrc</code>:
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>For Zsh, add to <code>~/.zshrc</code>:
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>Suggestions are from the wrong user</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// KNOWN LIMITATION</div>
                If you connect as one user and then run <code>sudo su - root</code> (or switch to another user with <code>su</code>), the suggestion bar still shows the history of the <em>original login user</em>, not of <code>root</code>. This is because SSHBorg reads the history file before the shell starts, using the credentials you connected with.
                <br><br>
                To get root's history suggestions, add a separate host entry in SSHBorg configured to log in directly as <code>root</code> (if your server allows it).
            </div>`,

  doc_terminal: `
            <h2>// TERMINAL GESTURES</h2>
            <p>The terminal responds to a few touch gestures beyond typing:</p>
            <ul>
                <li><strong>Scroll history</strong> — swipe up or down to browse the terminal's scrollback buffer.</li>
                <li><strong>Zoom</strong> — pinch to increase or decrease the text size.</li>
                <li><strong>Copy text</strong> — long-press anywhere on the terminal to enter selection mode. Drag the handles to adjust the selected area, then tap <em>Copy selection</em> to copy only the highlighted text, or <em>Copy all</em> to copy the entire output. Tap anywhere else to cancel.</li>
                <li><strong>Paste</strong> — use the <em>Paste</em> button in the extra-key bar (visible when the keyboard is open).</li>
            </ul>`,

  doc_terminal_settings: `
            <h2>// TERMINAL SETTINGS</h2>
            <p>Under <strong>Settings → Terminal</strong> you can adapt the terminal to your needs:</p>
            <ul>
                <li><strong>Terminal colors</strong> — the classic <em>dark</em> scheme (white on black), a <em>light</em> scheme (black on white) that is easier to read in bright daylight, or <em>match app theme</em>, which switches automatically together with the app theme. The change applies immediately, including to sessions that are already open.</li>
                <li><strong>Keep screen on</strong> — prevents the display from turning off while a terminal is open. Handy when watching logs or long-running commands. Off by default.</li>
                <li><strong>Default font size</strong> — the text size new terminal sessions start with; you can still pinch-zoom each session individually.</li>
                <li><strong>Scrollback</strong>, <strong>inverted scrolling</strong> and <strong>command suggestions</strong> — control how much output history is kept, the scroll direction, and the suggestion bar described above.</li>
                <li><strong>Double-tap action</strong> — optionally make a double-tap on the terminal send <em>Tab</em> (auto-complete), or two Tabs (list all candidates). Off by default.</li>
            </ul>`,

  doc_extra_keys: `
            <h2>// EXTRA KEY BAR</h2>
            <p>When the soft keyboard is open, a row of shortcut buttons appears above it. Scroll the bar sideways to reach all the keys.</p>
            <h3>Modifier keys</h3>
            <p><strong>Ctrl</strong> and <strong>Alt</strong> are sticky toggles — tap one, then tap a letter key to send the combination. They reset automatically after the next keystroke.</p>
            <ul>
                <li><strong>Ctrl+C</strong> — interrupt the running process.</li>
                <li><strong>Ctrl+D</strong> — send EOF / close the shell.</li>
                <li><strong>Ctrl+Z</strong> — suspend the process.</li>
                <li><strong>Ctrl+L</strong> — clear the screen.</li>
            </ul>
            <h3>Word mode</h3>
            <p>The spellcheck icon toggles the keyboard between <em>terminal mode</em> and <em>word mode</em>. In terminal mode (default) autocorrect and word suggestions are disabled — ideal for commands and file paths. In word mode the keyboard behaves like a normal text field, with suggestions and autocorrect enabled. Useful when typing natural language over SSH, for example with Claude Code or other interactive tools.</p>
            <h3>Navigation and editing</h3>
            <ul>
                <li><strong>ESC</strong> — Escape key.</li>
                <li><strong>Tab</strong> — shell autocomplete.</li>
                <li><strong>↑ ↓ ← →</strong> — cursor arrow keys.</li>
                <li><strong>Home / End</strong> — jump to the beginning or end of the line.</li>
                <li><strong>PgUp / PgDn</strong> — page up / page down.</li>
                <li><strong>Del</strong> — forward delete (character to the right of the cursor).</li>
                <li><strong>Paste</strong> — paste the clipboard into the terminal.</li>
            </ul>
            <h3>Function keys</h3>
            <p>Scroll the bar to the right to reach <strong>F1 through F12</strong>.</p>`,

  doc_agent_forwarding: `
            <h2>// AGENT FORWARDING</h2>
            <p>SSH agent forwarding lets the keys stored in SSHBorg be used to authenticate further connections made <em>from inside</em> the remote server — for example, to <code>git clone</code> a private repo, or to hop to a second machine.</p>
            <h3>Enabling forwarding in SSHBorg</h3>
            <p>When adding or editing a host, enable the <strong>Agent forwarding</strong> toggle. SSHBorg will act as an SSH agent for that session.</p>
            <h3>Server-side configuration</h3>
            <p>The server must allow agent forwarding. Check <code>/etc/ssh/sshd_config</code>:</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>This is the default on most systems. After changing it, restart the SSH daemon:</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>Per-host client configuration (optional)</h3>
            <p>If you also connect to this server from a laptop or desktop, you can configure forwarding persistently in your local <code>~/.ssh/config</code>:</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// SECURITY NOTE</div>
                Agent forwarding gives the remote server temporary access to your SSH agent socket. A root user (or a compromised process) on that server could use your keys to connect elsewhere while your session is active. Only enable forwarding on servers you trust.
            </div>`,

  doc_legacy_ciphers: `
            <h2>// LEGACY CIPHER SUPPORT</h2>
            <p>Some older servers — network appliances, embedded devices, or systems running outdated OpenSSH versions — only support cipher algorithms that modern SSH clients no longer advertise by default.</p>
            <p>When you enable the <strong>Allow Legacy Ciphers</strong> toggle in the host settings, SSHBorg appends the following additional algorithms to the negotiation list:</p>
            <h3>Added algorithms</h3>
            <ul>
                <li><strong>Ciphers:</strong> <code>aes128-cbc</code>, <code>aes192-cbc</code>, <code>aes256-cbc</code>, <code>3des-cbc</code></li>
                <li><strong>Key exchange:</strong> <code>diffie-hellman-group14-sha1</code>, <code>diffie-hellman-group-exchange-sha1</code>, <code>diffie-hellman-group1-sha1</code></li>
                <li><strong>Host key type:</strong> <code>ssh-dss</code> (DSA 1024-bit)</li>
            </ul>
            <p>The server always negotiates the strongest algorithm both sides support, so enabling this option does not weaken connections to modern servers — the legacy algorithms are only used when the server cannot offer anything better.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// SECURITY NOTE</div>
                The algorithms in this list are considered cryptographically weak. Enable this option only for servers you cannot upgrade.
            </div>`,

  doc_connection_drops: `
            <h2>// CONNECTION DROPS &amp; TERMINAL MULTIPLEXERS</h2>
            <p>SSH is a live TCP connection between your phone and the server. If the connection is interrupted — even for a second — the session and everything running inside it is lost.</p>
            <h3>Why connections drop on mobile</h3>
            <p>Mobile networks are particularly prone to connection drops for several reasons:</p>
            <ul>
                <li><strong>IP address changes</strong> — when travelling or switching between cell towers, your carrier may assign you a new public IP. Since TCP connections are tied to the IP address, the existing SSH session becomes invalid immediately.</li>
                <li><strong>Wi-Fi ↔ mobile handover</strong> — switching between a Wi-Fi network and mobile data (or vice versa) changes your IP and breaks any open TCP connection.</li>
                <li><strong>Idle timeouts</strong> — carriers and NAT routers often drop idle connections after a few minutes. Long-running but silent sessions (watching logs, waiting for input) are vulnerable to this.</li>
                <li><strong>Signal loss</strong> — tunnels, underground car parks, or simply a weak signal can briefly drop the network, which is enough to kill a session.</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANT</div>
                If you are running a long command (a build, a backup, a database migration) directly in the SSH terminal and the connection drops, the command is killed immediately. Any partial work may be left in an inconsistent state.
            </div>
            <h3>The solution: tmux or screen</h3>
            <p>A terminal multiplexer runs a persistent session <em>on the server</em>, completely independent of your SSH connection. If the connection drops, the session and everything running inside it keeps going. When you reconnect, you re-attach and find everything exactly as you left it.</p>
            <p>This is the single most useful habit for anyone managing servers from a phone.</p>
            <h3>Quick start with tmux</h3>
            <p><code>tmux</code> is available on most modern Linux distributions and is the recommended choice.</p>
            <pre><code># Start a new named session
tmux new -s work

# Detach from the session (leave it running)
Ctrl+B, then D

# List running sessions
tmux ls

# Re-attach to a session
tmux attach -t work

# Re-attach to the most recent session
tmux attach</code></pre>
            <h3>Quick start with screen</h3>
            <p><code>screen</code> is older but available on virtually every Unix system, including minimal server images where tmux may not be installed.</p>
            <pre><code># Start a new named session
screen -S work

# Detach from the session
Ctrl+A, then D

# List running sessions
screen -ls

# Re-attach to a session
screen -r work</code></pre>
            <h3>Recommended workflow on mobile</h3>
            <ol class="steps">
                <li>Connect to the server with SSHBorg.</li>
                <li>Start or re-attach a tmux/screen session immediately: <code>tmux attach || tmux new -s main</code></li>
                <li>Run your commands inside the multiplexer.</li>
                <li>If the connection drops, just reconnect — your session is still there.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// TIP</div>
                You can add <code>tmux attach || tmux new -s main</code> to your <code>~/.bashrc</code> or <code>~/.zshrc</code> on the server so that a multiplexer session starts automatically every time you log in via SSHBorg.
            </div>`,

  doc_jump_hosts: `
            <h2>// JUMP HOSTS</h2>
            <p>A jump host (also called a bastion host) is an intermediate server you must pass through to reach a target server that is not directly accessible from the internet. SSHBorg supports single and multi-hop jump chains natively.</p>
            <h3>Configuring a jump host in SSHBorg</h3>
            <ol class="steps">
                <li>Add the bastion server as a regular host in SSHBorg (e.g. <em>bastion</em>).</li>
                <li>Add the target server as another host.</li>
                <li>In the target host settings, set <strong>Jump host</strong> to the bastion host you created.</li>
                <li>Enable <strong>Agent forwarding</strong> on the bastion entry — this allows your key to be forwarded through the bastion to authenticate on the target.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// HOW IT WORKS</div>
                SSHBorg establishes an SSH connection to the bastion first, then opens a forwarded TCP channel through it to the target server. Your private key never leaves the phone — the bastion only proxies the encrypted stream.
            </div>
            <h3>Multi-hop chains</h3>
            <p>If you need to jump through more than one intermediate server (e.g. internet → bastion → dmz → target), create an entry for each hop and chain them:</p>
            <ul>
                <li><strong>bastion</strong> — no jump host, agent forwarding on</li>
                <li><strong>dmz</strong> — jump host = bastion, agent forwarding on</li>
                <li><strong>target</strong> — jump host = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANT</div>
                Agent forwarding must be enabled on <em>every intermediate hop</em>, not just the first one. Without it, the authentication chain breaks and the connection to the final server will fail with a "permission denied" error.
            </div>
            <h3>Equivalent manual configuration (for reference)</h3>
            <p>The equivalent setup in a desktop <code>~/.ssh/config</code> looks like this:</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>With this config, <code>ssh target</code> on your laptop transparently jumps through the bastion.</p>
            <h3>Firewall requirements</h3>
            <ul>
                <li>Your phone must be able to reach the bastion on its SSH port (usually 22).</li>
                <li>The bastion must be able to reach the target on its SSH port.</li>
                <li>The target does <em>not</em> need to be reachable directly from your phone.</li>
            </ul>`,

  doc_sessions: `
            <h2>// MULTIPLE SESSIONS</h2>
            <p>SSHBorg lets you keep several SSH terminal sessions and SFTP file manager sessions open at the same time, even to different servers.</p>
            <ul>
                <li>Open a session from the hosts screen by tapping <strong>Terminal</strong> or <strong>SFTP</strong>.</li>
                <li>Switch between open sessions using the session selector at the top of the screen.</li>
                <li>Sessions stay alive in the background as long as the network connection holds.</li>
                <li>The host list shows a small badge next to each host with the number of active SSH and SFTP sessions, so you can see at a glance what is open.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// TIP</div>
                Long-running commands (builds, backups, log tailing) keep running even when you switch to another session. Use a terminal multiplexer like <code>tmux</code> or <code>screen</code> on the server side if you want them to survive even if the SSH connection drops.
            </div>`,

  doc_security: `
            <h2>// APP SECURITY</h2>
            <h3>App lock</h3>
            <p>Choose how the app is protected in <strong>Settings → Security → App lock</strong>: <strong>None</strong> (default), <strong>Biometric only</strong> (fingerprint or face unlock), or <strong>Device lock</strong> — the device PIN, pattern, or password, alongside biometrics. When a lock is active, SSHBorg requires authentication before showing any host, credential, or session data.</p>
            <p>The <strong>Device lock</strong> option is useful on devices without biometric hardware — such as Android TV — where you can unlock with the system PIN instead.</p>
            <p>You can set an inactivity timeout — after that many minutes in the background the app locks automatically.</p>
            <h3>Screenshot protection</h3>
            <p>By default SSHBorg blocks screenshots and screen recording to prevent sensitive terminal content from leaking via the recent-apps screen or screen capture tools.</p>
            <p>If you need to take a screenshot (e.g. to share a terminal output), you can temporarily disable screenshot protection in <strong>Settings → Security → Allow screenshots</strong>.</p>
            <h3>Credential storage</h3>
            <p>All credentials (passwords, private keys, passphrases) are stored encrypted using the <strong>Android Keystore</strong> — a hardware-backed secure enclave available on Android 10+. They are never written to external storage or transmitted anywhere.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// BACKUP NOTE</div>
                Because keys are stored in the Android Keystore, they <strong>cannot be backed up</strong> via Android's cloud backup mechanism and will not transfer to a new phone automatically. Before switching devices, make sure to authorize a new key generated on the new device on all your servers.
            </div>`,

  doc_backup: `
            <h2>// CONFIGURATION BACKUP</h2>
            <p>SSHBorg can export and import host configurations and app settings as a JSON file. This lets you transfer your server list to another device or keep a portable backup of your setup.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANT</div>
                The backup includes your host configurations and app settings (terminal, appearance, behaviour). <strong>Passwords and SSH keys are never exported</strong> — the key material must be set up again on a new device. The backup does record the <em>name</em> of the key each host uses, so if you re-create a key with the same name before importing, its hosts are re-linked to it automatically.
            </div>
            <h3>Exporting</h3>
            <p>Go to <strong>Settings → Backup → Export backup</strong>. Choose where to save the file using the system file picker. The file is named <code>sshborg_backup.json</code> by default.</p>
            <h3>Importing</h3>
            <p>Go to <strong>Settings → Backup → Import backup</strong>. Select the <code>.json</code> file you previously exported (or created manually). SSHBorg merges it with the existing host list:</p>
            <ul>
                <li>Hosts whose <strong>label</strong> matches an existing entry are <strong>updated</strong>.</li>
                <li>Hosts with a new label are <strong>added</strong>.</li>
                <li>Hosts not present in the file are <strong>left unchanged</strong>.</li>
                <li>An updated host keeps its saved password, key and accepted host key — the backup never carries them.</li>
                <li>A host with no key is linked to a key whose name matches the exported <code>keyLabel</code>, if one exists; otherwise the field is ignored.</li>
</ul>
            <h3>JSON format</h3>
            <p>The export file is a plain JSON object. You can also create it by hand to bulk-import a server list from another source.</p>
            <pre><code>{
  "version": 4,
  "exported_at": "2026-05-14T10:00:00Z",
  "groups": [
    { "name": "Production", "color": -1754827 }
  ],
  "hosts": [
    {
      "label":           "My VPS",
      "hostname":        "203.0.113.42",
      "port":            22,
      "username":        "ubuntu",
      "agentForwarding": false,
      "jumpMode":        "simple",
      "jumpHosts":       null,
      "jumpHostIdList":  null,
      "portForwardings": null,
      "sftpStartMode":   "last",
      "sftpStartDir":    null,
      "allowLegacyCiphers": false,
      "keyLabel":         "My VPS key",
      "group":           "Production"
    }
  ],
  "settings": {
    "night_mode":            0,
    "scrollback_lines":      2000,
    "terminal_font_size":    13,
    "terminal_color_scheme": 0,
    "double_tap_action":     0
  }
}</code></pre>
            <p>The optional <code>settings</code> object holds your app preferences and is written automatically on export; you can leave it out when creating the file by hand.</p>
            <h3>Field reference</h3>
            <ul>
                <li><code>label</code> — display name shown in SSHBorg. Used as the unique key for merging on import. <strong>Required.</strong></li>
                <li><code>hostname</code> — server address or IP (IPv4 or IPv6). <strong>Required.</strong></li>
                <li><code>port</code> — SSH port. Defaults to <code>22</code>.</li>
                <li><code>username</code> — login user. <strong>Required.</strong></li>
                <li><code>agentForwarding</code> — <code>true</code> to enable SSH agent forwarding. Defaults to <code>false</code>.</li>
                <li><code>jumpMode</code> — <code>"simple"</code> (use the <code>jumpHosts</code> text) or <code>"host_list"</code> (use SSHBorg internal host IDs). Use <code>"simple"</code> when creating the file manually.</li>
                <li><code>jumpHosts</code> — comma-separated jump hosts in <code>[user@]host[:port]</code> format. Only used when <code>jumpMode</code> is <code>"simple"</code>.</li>
                <li><code>portForwardings</code> — newline-separated local port-forwarding rules in SSH <code>-L</code> syntax, e.g. <code>"8080:localhost:8080"</code>.</li>
                <li><code>sftpStartMode</code> — SFTP starting directory: <code>"last"</code> (remember last visited), <code>"fixed"</code> (always use <code>sftpStartDir</code>), <code>"home"</code> (server home). Defaults to <code>"last"</code>.</li>
                <li><code>sftpStartDir</code> — path to use when <code>sftpStartMode</code> is <code>"fixed"</code>.</li>
                <li><code>allowLegacyCiphers</code> — <code>true</code> to enable the legacy cipher algorithms described above. Defaults to <code>false</code>.</li>
                <li><code>keyLabel</code> — name of the SSH key this host uses. On import, if a key with this name exists it is linked to the host; otherwise the field is ignored. The key itself is never included in the backup.</li>
                <li><code>group</code> — name of the group the host belongs to. Groups are listed in the top-level <code>groups</code> array with their <code>name</code> and <code>color</code> (ARGB as a signed 32-bit integer). If a host references a group that is not in the array, it is created automatically with a default color — so you can omit the array entirely when writing the file by hand.</li>
                <li><code>color</code> — optional per-host color (ARGB as a signed 32-bit integer). It overrides the group color.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// OPTIONAL FIELDS</div>
                All fields except <code>label</code>, <code>hostname</code>, and <code>username</code> are optional. Omitted fields fall back to their defaults.
            </div>`,
};

'use strict';
module.exports = {
  page_title:        'SSHBorg – SSH- &amp; SFTP-Client für Android',
  meta_description:  'SSHBorg ist ein leistungsstarker SSH- und SFTP-Client für Android. Verwalte deine Server sicher vom Smartphone aus – mit Schlüssel-Authentifizierung, verschlüsseltem Speicher und biometrischer Sperre.',

  nav_features:  'Funktionen',
  nav_security:  'Sicherheit',
  nav_guide:     'Benutzerhandbuch',
  nav_support:   'Support',
  nav_privacy:   'Datenschutz',
  nav_tip:       'Trinkgeld geben',
  nav_contact:   'Kontakt',

  hero_sub: 'Ein leistungsstarker SSH- &amp; SFTP-Client für Android.<br>Verwalte deine Server sicher direkt vom Smartphone aus.',

  badge_no_ads:      'Keine Werbung',
  badge_no_tracking: 'Kein Tracking',
  badge_no_cloud:    'Keine Cloud',
  badge_free:        'Kostenlos',
  badge_android:     'Android 10+',

  cta_play: 'Bei Google Play herunterladen',

  features_title: '// FUNKTIONEN',

  feat_terminal_title: 'VOLLES SSH-TERMINAL',
  feat_terminal_desc:  'Interaktives Terminal mit VT100/xterm-Emulation, vollständiger UTF-8-Unterstützung und mehreren gleichzeitigen Sitzungen.',
  feat_sftp_title:     'SFTP-DATEIMANAGER',
  feat_sftp_desc:      'Dateien auf deinen Servern durchsuchen, hochladen, herunterladen, umbenennen und löschen – mit einem intuitiven Dateimanager.',
  feat_keys_title:     'SSH-SCHLÜSSEL-AUTH',
  feat_keys_desc:      'Ed25519-, ECDSA- und RSA-Schlüssel direkt auf deinem Gerät generieren. Kein Passwort erforderlich.',
  feat_jump_title:     'JUMP-HOST-UNTERSTÜTZUNG',
  feat_jump_desc:      'Verbinde dich über einen oder mehrere Bastion-Hosts mit transparentem Tunneling. Vollständiges SSH-Agent-Forwarding.',
  feat_biometric_title:'BIOMETRISCHE SPERRE',
  feat_biometric_desc: 'Schütze den Zugriff auf deine Server mit Fingerabdruck oder Gesichtserkennung. Konfigurierbares Timeout.',
  feat_multilingual_title: 'MEHRSPRACHIG',
  feat_multilingual_desc:  'Verfügbar in Englisch, Italienisch, Französisch, Deutsch, Spanisch, Portugiesisch und Ukrainisch.',
  feat_theme_title:    'DUNKLES &amp; HELLES DESIGN',
  feat_theme_desc:     'Folgt dem Systemdesign oder lässt dich selbst wählen. In jeder Lichtsituation gut lesbar.',
  feat_sessions_title: 'MEHRERE SITZUNGEN',
  feat_sessions_desc:  'Mehrere SSH- und SFTP-Sitzungen gleichzeitig offen halten. Sofort zwischen ihnen wechseln.',

  security_title: '// DATENSCHUTZ BY DESIGN',
  security_desc:  'SSHBorg erfasst niemals deine Daten. Alles bleibt auf deinem Gerät – Zugangsdaten, Schlüssel, Verbindungen.',

  sec_badge_keystore:   'Android-Keystore-Verschlüsselung',
  sec_badge_analytics:  'Keine Analysen',
  sec_badge_sdks:       'Keine Drittanbieter-SDKs',
  sec_badge_screenshots:'Screenshot-Schutz',
  sec_badge_opensource: 'Nur Open-Source-Bibliotheken',

  security_pp_link: 'Vollständige Datenschutzerklärung lesen &rarr;',

  tip_title:  '// TRINKGELD GEBEN',
  tip_desc:   'SSHBorg ist kostenlos, ohne Werbung und ohne Tracking. Wenn es dir Zeit spart, hilft ein kleines Trinkgeld dabei, es am Laufen zu halten.',
  kofi_cta:   'Unterstütze mich auf Ko-fi',

  footer_privacy: 'Datenschutz',
  footer_issues:  'Fehler &amp; Feedback',
  footer_powered: 'SSH-Konnektivität bereitgestellt von',

  page_title_docs:       'SSHBorg – Benutzerhandbuch',
  meta_description_docs: 'SSHBorg-Benutzerhandbuch: SSH-Schlüssel, Jump-Hosts, Agent-Forwarding, Befehlsvorschläge, tmux und mehr.',

  nav_home:           'Startseite',
  nav_getting_started:'Erste Schritte',
  nav_ssh_keys:       'SSH-Schlüssel',
  nav_jump_hosts:     'Jump-Hosts',

  doc_page_title:    '// BENUTZERHANDBUCH',
  doc_page_subtitle: 'Praxisanleitung — Schritt für Schritt, um das Beste aus SSHBorg herauszuholen.',

  toc_title: '// INHALT',

  doc_toc: `            <li><a href="#adding-host">Host hinzufügen</a></li>
            <li><a href="#ssh-keys">SSH-Schlüssel</a></li>
            <li class="sub"><a href="#ssh-keys">Schlüssel generieren</a></li>
            <li class="sub"><a href="#ssh-keys">Auf Server autorisieren</a></li>
            <li class="sub"><a href="#ssh-keys">Schlüsselsicherheit</a></li>
            <li><a href="#suggestions">Befehlsvorschläge</a></li>
            <li class="sub"><a href="#suggestions">Funktionsweise</a></li>
            <li class="sub"><a href="#suggestions">Fehlerbehebung</a></li>
            <li><a href="#agent-forwarding">Agent-Forwarding</a></li>
            <li><a href="#connection-drops">Verbindungsabbrüche</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">Jump-Hosts</a></li>
            <li class="sub"><a href="#jump-hosts">Multi-Hop-Ketten</a></li>
            <li><a href="#sessions">Mehrere Sitzungen</a></li>
            <li><a href="#security">App-Sicherheit</a></li>`,

  doc_adding_host: `
            <h2>// HOST HINZUFÜGEN</h2>
            <p>Tippe auf die Schaltfläche <strong>+</strong> im Host-Bildschirm, um einen neuen Server hinzuzufügen.</p>
            <h3>Pflichtfelder</h3>
            <ul>
                <li><strong>Hostname / IP</strong> — die Server-Adresse oder IP. Sowohl IPv4 als auch IPv6 werden unterstützt.</li>
                <li><strong>Port</strong> — Standard ist 22. Ändere ihn, wenn dein Server SSH auf einem anderen Port betreibt.</li>
                <li><strong>Benutzername</strong> — der Unix-Benutzer, mit dem du dich anmelden möchtest (z. B. <code>ubuntu</code>, <code>root</code>, <code>deploy</code>).</li>
                <li><strong>Authentifizierung</strong> — wähle zwischen Passwort oder SSH-Schlüssel (empfohlen).</li>
            </ul>
            <h3>Host-Fingerabdruck-Verifizierung</h3>
            <p>Bei der ersten Verbindung zeigt SSHBorg den Fingerabdruck des Servers und bittet dich, ihn zu akzeptieren. Dies ist eine Sicherheitsprüfung: Sie stellt sicher, dass du dich mit dem richtigen Gerät verbindest und nicht mit einem Angreifer. Überprüfe, ob der Fingerabdruck mit dem übereinstimmt, den dir dein Server-Administrator mitgeteilt hat oder den du über einen vertrauenswürdigen Kanal erhalten hast.</p>
            <p>Nach der Akzeptanz wird der Fingerabdruck lokal gespeichert. Wenn er sich bei einer zukünftigen Verbindung ändert, warnt dich SSHBorg — dies könnte auf einen Server-Neuaufbau, eine Schlüsselrotation oder einen Man-in-the-Middle-Angriff hinweisen.</p>
            <div class="callout callout-info">
                <div class="callout-label">// TIPP</div>
                Den Server-Fingerabdruck kannst du jederzeit überprüfen mit:
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_ssh_keys: `
            <h2>// SSH-SCHLÜSSEL</h2>
            <p>Schlüsselbasierte Authentifizierung ist sicherer als Passwörter und erfordert nach der Einrichtung kein Eintippen mehr.</p>
            <h3>Schlüssel generieren</h3>
            <p>Gehe zu <strong>Einstellungen → SSH-Schlüssel → Neuen Schlüssel generieren</strong>. SSHBorg unterstützt:</p>
            <ul>
                <li><strong>Ed25519</strong> — empfohlen. Schnell, kompakt und sicher.</li>
                <li><strong>ECDSA (P-256 / P-384)</strong> — gute Kompatibilität mit älteren Servern.</li>
                <li><strong>RSA (2048 / 4096 Bit)</strong> — maximale Kompatibilität, aber langsamer.</li>
            </ul>
            <p>Gib dem Schlüssel einen aussagekräftigen Namen (z. B. <em>mein-vps</em> oder <em>arbeits-server</em>), damit du ihn später wiedererkennen kannst.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// SICHERHEITSHINWEIS</div>
                SSHBorg erlaubt absichtlich keinen Export privater Schlüssel. Der Schlüssel verlässt das Gerät nie. Wenn du denselben Schlüssel auf einem anderen Gerät benötigst, generiere dort einen neuen und autorisiere ihn separat auf deinen Servern — das ist der sicherere Ansatz.
            </div>
            <h3>Schlüssel auf dem Server autorisieren</h3>
            <p>Nach dem Generieren eines Schlüssels tippe darauf, um den <strong>öffentlichen Schlüssel</strong> zu sehen. Kopiere ihn und füge ihn in die Datei <code>~/.ssh/authorized_keys</code> des Servers für den gewünschten Benutzer ein.</p>
            <ol class="steps">
                <li>Öffne auf deinem Smartphone SSHBorg → Einstellungen → SSH-Schlüssel → tippe auf den Schlüssel → öffentlichen Schlüssel kopieren.</li>
                <li>Melde dich bei deinem Server an (mit Passwort oder einem anderen bereits vorhandenen Schlüssel).</li>
                <li>Füge den öffentlichen Schlüssel zur Authorized-Keys-Datei hinzu:
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...deinkopierterSchlüssel..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>Versuche, dich mit SSHBorg zu verbinden — es sollte sich ohne Passwortabfrage anmelden.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// SERVER-VORAUSSETZUNG</div>
                Stelle sicher, dass dein Server <code>PubkeyAuthentication yes</code> in <code>/etc/ssh/sshd_config</code> hat. Dies ist auf den meisten Distributionen der Standard, aber einige gehärtete Images deaktivieren es.
            </div>
            <h3>Zusätzliche Schlüsselverschlüsselung</h3>
            <p>SSHBorg bietet eine optionale <strong>zusätzliche Passphrase</strong> für deine Schlüssel (Einstellungen → SSH-Schlüssel → Schlüssel antippen → Verschlüsselung aktivieren). Wenn aktiviert, wird der Schlüssel mit einer Passphrase verschlüsselt, die SSHBorg nicht speichert — du wirst jedes Mal danach gefragt, wenn der Schlüssel verwendet wird.</p>
            <p>Dies wird dringend empfohlen, wenn du sensible Server-Zugangsdaten auf deinem Smartphone speicherst oder die biometrische Sperre deaktiviert hast.</p>`,

  doc_suggestions: `
            <h2>// BEFEHLSVORSCHLÄGE</h2>
            <p>SSHBorg zeigt eine Vorschlagsleiste über der Tastatur an, während du im Terminal tippst. Die Vorschläge stammen aus der Shell-Historie des Benutzers, mit dem du dich verbunden hast.</p>
            <h3>Funktionsweise</h3>
            <p>Wenn eine Terminal-Sitzung geöffnet wird, liest SSHBorg die Shell-Historiendatei vom Remote-Server. Es werden folgende Pfade der Reihe nach geprüft:</p>
            <ol>
                <li><code>~/.bash_history</code> — Standard für Bash-Shells</li>
                <li><code>~/.zsh_history</code> — Standard für Zsh (wird auch als <code>$HISTFILE</code> geprüft, wenn gesetzt)</li>
                <li><code>~/.local/share/fish/fish_history</code> — für Fish-Shell-Benutzer</li>
            </ol>
            <p>Die erste vorhandene und lesbare Datei wird verwendet. Während du tippst, werden Befehle in Echtzeit gefiltert und als Chips in der Vorschlagsleiste angezeigt. Tippe auf einen Chip, um den Befehl einzufügen.</p>
            <h3>Fehlerbehebung</h3>
            <p><strong>Es werden keine Vorschläge angezeigt</strong></p>
            <ul>
                <li>Die Historiendatei existiert möglicherweise noch nicht (erste Anmeldung oder Shell nicht für das Speichern der Historie konfiguriert).</li>
                <li>Stelle sicher, dass deine Shell die Historie speichert. Für Bash füge zu <code>~/.bashrc</code> hinzu:
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>Für Zsh füge zu <code>~/.zshrc</code> hinzu:
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>Vorschläge gehören dem falschen Benutzer</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// BEKANNTE EINSCHRÄNKUNG</div>
                Wenn du dich als ein Benutzer verbindest und dann <code>sudo su - root</code> ausführst (oder mit <code>su</code> zu einem anderen Benutzer wechselst), zeigt die Vorschlagsleiste weiterhin die Historie des <em>ursprünglichen Anmeldebenutzers</em>, nicht von <code>root</code>. Dies liegt daran, dass SSHBorg die Historiendatei liest, bevor die Shell startet, und dabei die Anmeldedaten verwendet.
                <br><br>
                Um Vorschläge aus der Root-Historie zu erhalten, füge in SSHBorg einen separaten Host-Eintrag hinzu, der direkt als <code>root</code> anmeldet (sofern dein Server dies erlaubt).
            </div>`,

  doc_agent_forwarding: `
            <h2>// AGENT-FORWARDING</h2>
            <p>SSH-Agent-Forwarding ermöglicht es, die in SSHBorg gespeicherten Schlüssel für weitere Verbindungen zu verwenden, die <em>vom Remote-Server aus</em> hergestellt werden — zum Beispiel für <code>git clone</code> eines privaten Repos oder um zu einer zweiten Maschine zu springen.</p>
            <h3>Forwarding in SSHBorg aktivieren</h3>
            <p>Beim Hinzufügen oder Bearbeiten eines Hosts aktiviere den Schalter <strong>Agent-Forwarding</strong>. SSHBorg fungiert dann als SSH-Agent für diese Sitzung.</p>
            <h3>Server-seitige Konfiguration</h3>
            <p>Der Server muss Agent-Forwarding erlauben. Überprüfe <code>/etc/ssh/sshd_config</code>:</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>Dies ist auf den meisten Systemen der Standard. Nach einer Änderung den SSH-Daemon neu starten:</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>Client-Konfiguration pro Host (optional)</h3>
            <p>Wenn du dich auch von Laptop oder Desktop mit diesem Server verbindest, kannst du das Forwarding dauerhaft in deiner lokalen <code>~/.ssh/config</code> konfigurieren:</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// SICHERHEITSHINWEIS</div>
                Agent-Forwarding gibt dem Remote-Server vorübergehend Zugriff auf deinen SSH-Agent-Socket. Ein Root-Benutzer (oder ein kompromittierter Prozess) auf diesem Server könnte deine Schlüssel verwenden, um sich anderswo zu verbinden, solange deine Sitzung aktiv ist. Aktiviere Forwarding nur auf Servern, denen du vertraust.
            </div>`,

  doc_connection_drops: `
            <h2>// VERBINDUNGSABBRÜCHE &amp; TERMINAL-MULTIPLEXER</h2>
            <p>SSH ist eine aktive TCP-Verbindung zwischen deinem Smartphone und dem Server. Wenn die Verbindung unterbrochen wird — auch nur für eine Sekunde — gehen die Sitzung und alles, was darin läuft, verloren.</p>
            <h3>Warum Verbindungen auf Mobilgeräten abbrechen</h3>
            <p>Mobilfunknetze sind aus mehreren Gründen besonders anfällig für Verbindungsabbrüche:</p>
            <ul>
                <li><strong>IP-Adressänderungen</strong> — beim Reisen oder Wechseln zwischen Sendemasten kann dir dein Anbieter eine neue öffentliche IP zuweisen. Da TCP-Verbindungen an die IP-Adresse gebunden sind, wird die bestehende SSH-Sitzung sofort ungültig.</li>
                <li><strong>WLAN ↔ Mobilfunk-Wechsel</strong> — das Wechseln zwischen WLAN und Mobilfunkdaten (oder umgekehrt) ändert deine IP und unterbricht alle offenen TCP-Verbindungen.</li>
                <li><strong>Inaktivitäts-Timeouts</strong> — Mobilfunkanbieter und NAT-Router beenden oft inaktive Verbindungen nach wenigen Minuten. Lang laufende, aber stille Sitzungen (Log-Beobachtung, auf Eingabe warten) sind gefährdet.</li>
                <li><strong>Signalverlust</strong> — Tunnel, Tiefgaragen oder einfach ein schwaches Signal können kurz die Netzwerkverbindung unterbrechen, was ausreicht, um eine Sitzung zu beenden.</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// WICHTIG</div>
                Wenn du einen langen Befehl (einen Build, ein Backup, eine Datenbankmigration) direkt im SSH-Terminal ausführst und die Verbindung abbricht, wird der Befehl sofort beendet. Eventuell hinterlässt er einen inkonsistenten Zustand.
            </div>
            <h3>Die Lösung: tmux oder screen</h3>
            <p>Ein Terminal-Multiplexer führt eine persistente Sitzung <em>auf dem Server</em> aus, völlig unabhängig von deiner SSH-Verbindung. Wenn die Verbindung abbricht, läuft die Sitzung weiter. Nach der Wiederverbindung hängst du dich wieder ein und findest alles genau so vor, wie du es verlassen hast.</p>
            <p>Dies ist die nützlichste Gewohnheit für alle, die Server vom Smartphone aus verwalten.</p>
            <h3>Schnellstart mit tmux</h3>
            <p><code>tmux</code> ist auf den meisten modernen Linux-Distributionen verfügbar und die empfohlene Wahl.</p>
            <pre><code># Neue benannte Sitzung starten
tmux new -s work

# Von der Sitzung trennen (laufen lassen)
Ctrl+B, dann D

# Laufende Sitzungen auflisten
tmux ls

# Sitzung wieder einbinden
tmux attach -t work

# Zuletzt verwendete Sitzung wieder einbinden
tmux attach</code></pre>
            <h3>Schnellstart mit screen</h3>
            <p><code>screen</code> ist älter, aber auf praktisch jedem Unix-System verfügbar, auch auf minimalen Server-Images, auf denen tmux möglicherweise nicht installiert ist.</p>
            <pre><code># Neue benannte Sitzung starten
screen -S work

# Von der Sitzung trennen
Ctrl+A, dann D

# Laufende Sitzungen auflisten
screen -ls

# Sitzung wieder einbinden
screen -r work</code></pre>
            <h3>Empfohlener Workflow auf Mobilgeräten</h3>
            <ol class="steps">
                <li>Mit SSHBorg mit dem Server verbinden.</li>
                <li>Sofort eine tmux/screen-Sitzung starten oder wieder einbinden: <code>tmux attach || tmux new -s main</code></li>
                <li>Befehle innerhalb des Multiplexers ausführen.</li>
                <li>Falls die Verbindung abbricht, einfach neu verbinden — die Sitzung ist noch da.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// TIPP</div>
                Du kannst <code>tmux attach || tmux new -s main</code> zu deiner <code>~/.bashrc</code> oder <code>~/.zshrc</code> auf dem Server hinzufügen, damit bei jeder Anmeldung über SSHBorg automatisch eine Multiplexer-Sitzung gestartet wird.
            </div>`,

  doc_jump_hosts: `
            <h2>// JUMP-HOSTS</h2>
            <p>Ein Jump-Host (auch Bastion-Host genannt) ist ein Zwischenserver, durch den du einen Zielserver erreichen musst, der nicht direkt aus dem Internet zugänglich ist. SSHBorg unterstützt einfache und mehrstufige Jump-Ketten nativ.</p>
            <h3>Jump-Host in SSHBorg konfigurieren</h3>
            <ol class="steps">
                <li>Den Bastion-Server als normalen Host in SSHBorg hinzufügen (z. B. <em>bastion</em>).</li>
                <li>Den Zielserver als weiteren Host hinzufügen.</li>
                <li>In den Einstellungen des Ziel-Hosts <strong>Jump-Host</strong> auf den erstellten Bastion-Host setzen.</li>
                <li><strong>Agent-Forwarding</strong> auf dem Bastion-Eintrag aktivieren — damit kann dein Schlüssel durch den Bastion weitergeleitet werden, um sich auf dem Ziel zu authentifizieren.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// FUNKTIONSWEISE</div>
                SSHBorg stellt zuerst eine SSH-Verbindung zum Bastion her und öffnet dann einen weitergeleiteten TCP-Kanal durch diesen zum Zielserver. Der private Schlüssel verlässt niemals das Smartphone — der Bastion leitet nur den verschlüsselten Datenstrom weiter.
            </div>
            <h3>Multi-Hop-Ketten</h3>
            <p>Wenn du durch mehrere Zwischenserver springen musst (z. B. Internet → Bastion → DMZ → Ziel), erstelle einen Eintrag für jeden Hop und verkette sie:</p>
            <ul>
                <li><strong>bastion</strong> — kein Jump-Host, Agent-Forwarding aktiv</li>
                <li><strong>dmz</strong> — Jump-Host = bastion, Agent-Forwarding aktiv</li>
                <li><strong>target</strong> — Jump-Host = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// WICHTIG</div>
                Agent-Forwarding muss auf <em>jedem Zwischenhop</em> aktiviert sein, nicht nur auf dem ersten. Ohne dies bricht die Authentifizierungskette ab und die Verbindung zum letzten Server schlägt mit einem „Permission denied"-Fehler fehl.
            </div>
            <h3>Äquivalente manuelle Konfiguration (zur Referenz)</h3>
            <p>Die entsprechende Konfiguration in einer Desktop-<code>~/.ssh/config</code> sieht so aus:</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>Mit dieser Konfiguration springt <code>ssh target</code> auf dem Laptop transparent durch den Bastion.</p>
            <h3>Firewall-Anforderungen</h3>
            <ul>
                <li>Das Smartphone muss den Bastion über seinen SSH-Port (normalerweise 22) erreichen können.</li>
                <li>Der Bastion muss das Ziel über seinen SSH-Port erreichen können.</li>
                <li>Das Ziel muss <em>nicht</em> direkt vom Smartphone aus erreichbar sein.</li>
            </ul>`,

  doc_sessions: `
            <h2>// MEHRERE SITZUNGEN</h2>
            <p>SSHBorg ermöglicht es, mehrere SSH-Terminal- und SFTP-Dateimanager-Sitzungen gleichzeitig offen zu halten, auch zu verschiedenen Servern.</p>
            <ul>
                <li>Sitzungen über den Host-Bildschirm öffnen durch Antippen von <strong>Terminal</strong> oder <strong>SFTP</strong>.</li>
                <li>Mit dem Sitzungsauswähler oben im Bildschirm zwischen offenen Sitzungen wechseln.</li>
                <li>Sitzungen bleiben im Hintergrund aktiv, solange die Netzwerkverbindung besteht.</li>
                <li>Die Host-Liste zeigt ein kleines Abzeichen neben jedem Host mit der Anzahl aktiver SSH- und SFTP-Sitzungen, sodass du auf einen Blick sehen kannst, was offen ist.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// TIPP</div>
                Lang laufende Befehle (Builds, Backups, Log-Beobachtung) laufen weiter, auch wenn du zu einer anderen Sitzung wechselst. Verwende einen Terminal-Multiplexer wie <code>tmux</code> oder <code>screen</code> auf der Server-Seite, wenn sie auch einen SSH-Verbindungsabbruch überleben sollen.
            </div>`,

  doc_security: `
            <h2>// APP-SICHERHEIT</h2>
            <h3>Biometrische Sperre</h3>
            <p>Aktiviere die biometrische Sperre unter <strong>Einstellungen → Sicherheit → Biometrische Sperre</strong>. Wenn aktiv, erfordert SSHBorg einen Fingerabdruck oder Gesichtserkennung, bevor Host-, Anmelde- oder Sitzungsdaten angezeigt werden.</p>
            <p>Du kannst ein Inaktivitäts-Timeout festlegen — nach dieser Anzahl von Minuten im Hintergrund sperrt sich die App automatisch.</p>
            <h3>Screenshot-Schutz</h3>
            <p>Standardmäßig blockiert SSHBorg Screenshots und Bildschirmaufnahmen, um zu verhindern, dass sensible Terminal-Inhalte über den Zuletzt-Geöffnet-Bildschirm oder Aufnahme-Tools durchsickern.</p>
            <p>Wenn du einen Screenshot machen musst (z. B. um eine Terminal-Ausgabe zu teilen), kannst du den Screenshot-Schutz vorübergehend unter <strong>Einstellungen → Sicherheit → Screenshots erlauben</strong> deaktivieren.</p>
            <h3>Anmeldedatenspeicherung</h3>
            <p>Alle Anmeldedaten (Passwörter, private Schlüssel, Passphrasen) werden verschlüsselt im <strong>Android Keystore</strong> gespeichert — einem hardware-gestützten sicheren Speicher, der auf Android 10+ verfügbar ist. Sie werden niemals auf externen Speicher geschrieben oder übertragen.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// HINWEIS ZUM BACKUP</div>
                Da Schlüssel im Android Keystore gespeichert sind, können sie <strong>nicht über Androids Cloud-Backup</strong> gesichert werden und werden nicht automatisch auf ein neues Gerät übertragen. Stelle vor einem Gerätewechsel sicher, dass du einen auf dem neuen Gerät generierten Schlüssel auf allen deinen Servern autorisierst.
            </div>`,
};

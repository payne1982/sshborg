'use strict';
module.exports = {
  page_title:        'SSHBorg – Client SSH e SFTP per Android e iOS',
  meta_description:  'SSHBorg è un potente client SSH e SFTP per Android, iPhone e iPad. Gestisci i tuoi server in sicurezza dal telefono con autenticazione a chiave, storage cifrato e blocco biometrico.',

  nav_features:  'Funzionalità',
  nav_security:  'Sicurezza',
  nav_guide:     'Guida utente',
  nav_support:   'Supporto',
  nav_privacy:   'Privacy Policy',
  nav_tip:       'Lascia una mancia',
  nav_contact:   'Contatto',

  hero_sub: 'Un potente client SSH &amp; SFTP per Android e iOS.<br>Gestisci i tuoi server in sicurezza, direttamente dal telefono.',

  badge_no_ads:      'Senza pubblicità',
  badge_no_tracking: 'Senza tracciamento',
  badge_no_cloud:    'Senza cloud',
  badge_free:        'Gratuito',
  badge_android:     'Android 10+',
  badge_ios: 'iOS 16+',

  cta_download:   'SCARICA',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',
  cta_appstore: 'App Store',

  features_title: '// FUNZIONALITÀ',

  feat_terminal_title: 'TERMINALE SSH COMPLETO',
  feat_terminal_desc:  'Terminale interattivo con emulazione VT100/xterm, supporto UTF-8 completo e sessioni multiple simultanee.',
  feat_sftp_title:     'FILE MANAGER SFTP',
  feat_sftp_desc:      'Naviga, carica, scarica, rinomina ed elimina file sui tuoi server con un file manager intuitivo.',
  feat_keys_title:     'AUTENTICAZIONE A CHIAVE',
  feat_keys_desc:      'Genera chiavi Ed25519, ECDSA e RSA direttamente sul tuo dispositivo. Nessuna password necessaria.',
  feat_jump_title:     'JUMP HOST',
  feat_jump_desc:      'Connettiti attraverso uno o più bastion host con tunnelling trasparente. Forwarding completo dell\'agente SSH.',
  feat_biometric_title:'BLOCCO APP',
  feat_biometric_desc: 'Blocca l\'app con la biometria o il codice del dispositivo — e su Android anche con un PIN o una passphrase in-app, che funziona perfino su Android TV. Timeout configurabile.',
  feat_multilingual_title: 'MULTILINGUA',
  feat_multilingual_desc:  'Disponibile in inglese, italiano, francese, tedesco, spagnolo, portoghese, ucraino, russo, cinese e giapponese.',
  feat_theme_title:    'TEMA SCURO E CHIARO',
  feat_theme_desc:     'Segue il tema di sistema o ti lascia scegliere. Perfettamente leggibile in qualsiasi condizione di luce.',
  feat_sessions_title: 'SESSIONI MULTIPLE',
  feat_sessions_desc:  'Tieni aperte più sessioni SSH e SFTP contemporaneamente. Passa da una all\'altra istantaneamente.',

  security_title: '// PRIVACY BY DESIGN',
  security_desc:  'SSHBorg non raccoglie mai i tuoi dati. Tutto rimane sul tuo dispositivo — credenziali, chiavi, connessioni.',

  sec_badge_keystore:   'Cifratura hardware del dispositivo',
  sec_badge_analytics:  'Nessuna analisi',
  sec_badge_sdks:       'Nessun SDK di terze parti',
  sec_badge_screenshots:'Protezione dei contenuti a schermo',
  sec_badge_opensource: 'Open source — GPL v3',

  security_pp_link: 'Leggi la Privacy Policy completa &rarr;',

  tip_title:  '// LASCIA UNA MANCIA',
  tip_desc:   'SSHBorg è gratuito, senza pubblicità e senza tracciamento. Se ti fa risparmiare tempo, una piccola mancia aiuta a mantenerlo.',
  kofi_cta:        'Supportami su Ko-fi',
  kofi_hero_cta:   'Lasciami una mancia',
  support_title: '// SUPPORTO',
  support_desc: 'Hai trovato un bug o hai un\'idea? Apri una segnalazione nel repository dell\'app che usi — oppure scrivimi.',
  support_issues_android: 'Segnalazioni Android',
  support_issues_ios: 'Segnalazioni iOS',

  footer_privacy: 'Privacy Policy',
  footer_issues:  'Segnalazioni &amp; Feedback',
  footer_source:  'Codice sorgente',
  footer_licenses: 'Licenze',
  footer_powered: 'Connettività SSH realizzata con',

  // ── docs.html ──────────────────────────────────────────────────────────────
  page_title_docs:       'SSHBorg – Guida utente',
  meta_description_docs: 'Guida operativa di SSHBorg per Android e iOS: chiavi SSH, jump host, agent forwarding, suggerimenti comandi, tmux e molto altro.',

  nav_home:           'Home',
  nav_getting_started:'Per iniziare',
  nav_ssh_keys:       'Chiavi SSH',
  nav_jump_hosts:     'Jump Host',
  nav_sftp:           'SFTP',
  nav_backup:         'Backup',

  doc_page_title:    '// GUIDA UTENTE',
  doc_page_subtitle: 'Guida operativa — cosa fare, passo dopo passo, per sfruttare al massimo SSHBorg su Android e iOS. Dove l\'app iOS si comporta diversamente, lo dice una nota contrassegnata iOS.',

  toc_title: '// INDICE',

  doc_toc: `            <li><a href="#adding-host">Aggiungere un host</a></li>
            <li><a href="#host-groups">Gruppi di host</a></li>
            <li class="sub"><a href="#host-groups">Ordinamento</a></li>
            <li><a href="#sftp">File manager SFTP</a></li>
            <li class="sub"><a href="#sftp">Navigazione</a></li>
            <li class="sub"><a href="#sftp">Carica e scarica</a></li>
            <li class="sub"><a href="#sftp">Selezione multipla</a></li>
            <li class="sub"><a href="#sftp">Modificare i file</a></li>
            <li class="sub"><a href="#sftp">Editor esadecimale</a></li>
            <li><a href="#ssh-keys">Chiavi SSH</a></li>
            <li class="sub"><a href="#ssh-keys">Generare una chiave</a></li>
            <li class="sub"><a href="#ssh-keys">Autorizzare sul server</a></li>
            <li class="sub"><a href="#ssh-keys">Sicurezza delle chiavi</a></li>
            <li><a href="#suggestions">Suggerimenti comandi</a></li>
            <li class="sub"><a href="#suggestions">Come funziona</a></li>
            <li class="sub"><a href="#suggestions">Risoluzione problemi</a></li>
            <li><a href="#terminal">Gesture del terminale</a></li>
            <li><a href="#terminal-settings">Impostazioni del terminale</a></li>
            <li><a href="#extra-keys">Barra tasti extra</a></li>
            <li><a href="#agent-forwarding">Agent Forwarding</a></li>
            <li><a href="#legacy-ciphers">Cifrature Legacy</a></li>
            <li><a href="#connection-drops">Connessioni instabili</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">Jump Host</a></li>
            <li class="sub"><a href="#jump-hosts">Catene multi-hop</a></li>
            <li><a href="#sessions">Sessioni multiple</a></li>
            <li><a href="#security">Sicurezza dell'app</a></li>
            <li class="sub"><a href="#security">L'impostazione più sicura</a></li>
            <li><a href="#backup">Backup configurazione</a></li>
            <li><a href="#android-tv">Android TV</a></li>
            <li><a href="#licenses">Licenze open source</a></li>`,

  doc_adding_host: `
            <h2>// AGGIUNGERE UN HOST</h2>
            <p>Tocca il pulsante <strong>+</strong> nella schermata degli host per aggiungere un nuovo server (su iOS apre un piccolo menu: scegli <em>Aggiungi Host</em>).</p>
            <h3>Campi obbligatori</h3>
            <ul>
                <li><strong>Hostname / IP</strong> — l'indirizzo o l'IP del server. Sono supportati sia IPv4 che IPv6.</li>
                <li><strong>Porta</strong> — predefinita a 22. Modificala se il tuo server usa una porta SSH diversa.</li>
                <li><strong>Nome utente</strong> — l'utente Unix con cui vuoi accedere (es. <code>ubuntu</code>, <code>root</code>, <code>deploy</code>).</li>
                <li><strong>Autenticazione</strong> — scegli tra password o chiave SSH (consigliata).</li>
            </ul>
            <h3>Verifica del fingerprint</h3>
            <p>Alla prima connessione SSHBorg mostra il fingerprint del server e chiede di accettarlo. È un controllo di sicurezza: garantisce che ti stai connettendo alla macchina giusta e non a un impostore. Verifica che il fingerprint corrisponda a quello fornito dall'amministratore del server o ottenuto tramite un canale affidabile prima di accettare.</p>
            <p>Una volta accettato, il fingerprint viene salvato localmente. Se cambia in una connessione futura, SSHBorg ti avviserà — potrebbe indicare una reinstallazione del server, una rotazione delle chiavi o un attacco man-in-the-middle.</p>
            <div class="callout callout-info">
                <div class="callout-label">// SUGGERIMENTO</div>
                Puoi controllare il fingerprint del server in qualsiasi momento con:
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_host_groups: `
            <h2>// GRUPPI DI HOST</h2>
            <p>Quando l'elenco dei server cresce, puoi organizzare gli host in gruppi richiudibili e colorati — ad esempio <em>Produzione</em>, <em>Lab di casa</em>, <em>Clienti</em>.</p>
            <h3>Creare un gruppo</h3>
            <ol class="steps">
                <li>Aggiungi o modifica un host e apri il menu <strong>Gruppo</strong>.</li>
                <li>Scegli <strong>Nuovo gruppo…</strong>, digita un nome e seleziona uno dei colori predefiniti.</li>
                <li>Salva l'host — l'elenco degli host ora mostra il gruppo come sezione dedicata.</li>
            </ol>
            <p>Su iOS un gruppo si può creare anche da solo, dal pulsante <strong>+</strong> → <em>Nuovo gruppo</em>.</p>
            <h3>Usare i gruppi</h3>
            <ul>
                <li><strong>Comprimi / espandi</strong> — tocca l'intestazione di un gruppo per chiuderlo o riaprirlo. Lo stato viene ricordato, anche dopo il riavvio dell'app.</li>
                <li><strong>Colore</strong> — il colore del gruppo appare come pallino nell'intestazione e come tinta delle icone degli host del gruppo.</li>
                <li><strong>Modifica</strong> — tieni premuta l'intestazione e scegli <em>Modifica</em> per rinominare il gruppo o cambiarne il colore.</li>
                <li><strong>Elimina</strong> — tieni premuta l'intestazione e scegli <em>Elimina</em>. Gli host del gruppo <em>non</em> vengono cancellati: tornano semplicemente senza gruppo.</li>
            </ul>
            <p>I colori si scelgono dagli swatch rapidi o, su Android, liberamente con il selettore a gradiente — su iOS si sceglie tra gli swatch. Un host può anche avere un <strong>colore proprio</strong> — si imposta nell'editor dell'host, subito sotto il gruppo — che prevale sul colore del gruppo e funziona anche per gli host senza gruppo.</p>
            <p>Gli host senza gruppo restano in cima all'elenco e, se non crei alcun gruppo, l'elenco appare e si comporta esattamente come prima.</p>
            <h3>Ordinamento</h3>
            <p>Scegli come disporre la lista da <strong>Impostazioni → Ordinamento host</strong>:</p>
            <ul>
                <li><strong>Alfabetico</strong> (predefinito) — gruppi per nome, host per etichetta dentro ciascuno.</li>
                <li><strong>Recenti</strong> — in cima l'host a cui ti sei collegato per ultimo; quelli mai usati restano in fondo.</li>
                <li><strong>Più usati</strong> — prima gli host con più connessioni.</li>
                <li><strong>Manuale</strong> — l'ordine che imposti tu.</li>
            </ul>
            <p>Nelle prime tre modalità i gruppi restano in ordine alfabetico, così le intestazioni non si spostano mai e cambia solo la disposizione degli host al loro interno. Nell'ordine manuale si possono spostare anche i gruppi.</p>
            <p>Per riordinare scegli <strong>Manuale</strong>, poi apri il menu di un host o di un'intestazione di gruppo — il pulsante <strong>⋮</strong> (<strong>…</strong> su iOS), una pressione prolungata oppure il tasto <em>Menu</em> del telecomando su Android TV — e usa <strong>Sposta su</strong> / <strong>Sposta giù</strong>. Un host si sposta solo dentro la propria sezione: per metterlo in un altro gruppo cambia il gruppo nell'editor dell'host. Passando a manuale viene mantenuto esattamente l'ordine che avevi a schermo in quel momento, quindi non si scompiglia niente, e gli host aggiunti in seguito finiscono in fondo alla loro sezione.</p>`,

  doc_sftp: `
            <h2>// FILE MANAGER SFTP</h2>
            <p>Il file manager SFTP ti permette di navigare, caricare, scaricare, rinominare ed eliminare file sul server direttamente dal telefono. Aprilo dalla schermata degli host con <strong>File</strong> nel menu dell'host.</p>
            <h3>Navigazione</h3>
            <p>Tocca una cartella per aprirla. Usa la freccia indietro o tocca qualsiasi segmento della barra del percorso per risalire nell'albero delle directory.</p>
            <p>I link simbolici sono mostrati con un piccolo badge a forma di catena. Toccare un link simbolico naviga verso la sua destinazione: se punta a una cartella la si entra, se punta a un file si comporta come un file normale.</p>
            <h3>Caricamento file</h3>
            <p>Tocca il pulsante di <strong>caricamento</strong> (↑) per scegliere uno o più file dallo storage del telefono. Il caricamento inizia immediatamente e il progresso viene mostrato in cima alla schermata.</p>
            <h3>Download di file e cartelle</h3>
            <p>Tocca un file qualsiasi per scaricarlo immediatamente. Per scaricare un'intera cartella, tocca l'icona di <strong>download</strong> accanto ad essa — SSHBorg scaricherà l'intero albero di directory e lo salverà nella cartella <strong>Download</strong> del telefono.</p>
            <p>Se un file esiste già nella destinazione, un dialogo chiederà se <strong>sovrascrivere</strong>, <strong>saltare</strong> il file, oppure <strong>annullare</strong> l'intero trasferimento.</p>
            <div class="callout callout-info">
                <div class="callout-label">// NOTA SUI LINK SIMBOLICI</div>
                Durante il download di una cartella, i link simbolici che puntano a directory vengono saltati — vengono scaricati solo i file normali (inclusi i link simbolici che puntano a file). Questo evita download ricorsivi involontari.
            </div>
            <h3>Selezione multipla e operazioni batch</h3>
            <p>Tieni premuto un elemento per entrare in modalità selezione, poi tocca altri elementi per aggiungere alla selezione. La barra degli strumenti mostra le azioni disponibili:</p>
            <ul>
                <li><strong>Scarica</strong> — scarica tutti i file e le cartelle selezionati in una volta, con un dialogo di avanzamento e supporto all'annullamento.</li>
                <li><strong>Elimina</strong> — elimina tutti gli elementi selezionati. L'eliminazione di una cartella non vuota rimuove tutto il suo contenuto ricorsivamente. <em>Non è possibile annullare l'operazione.</em></li>
            </ul>
            <h3>Modificare i file</h3>
            <p>Apri il menu di un file — pressione prolungata, oppure il pulsante <strong>⋮</strong> — e scegli <strong>Apri nell'editor</strong> per modificarlo direttamente sul server. Sul telefono non resta niente: il file viene letto in memoria, modificato e riscritto sul server.</p>
            <p>Il file torna com'era arrivato. La codifica dei caratteri viene riconosciuta e riusata al salvataggio, la fine riga LF o CRLF viene mantenuta, un file che finiva senza a capo continua a finire senza, e i permessi restano quelli. Se la codifica è stata letta male, toccala nella barra in fondo all'editor e scegline un'altra — l'elenco propone solo codifiche in grado di riprodurre esattamente i byte di quel file, quindi una scelta sbagliata può sembrare sbagliata a schermo ma non può rovinare il file.</p>
            <p>Il salvataggio scrive su un file temporaneo affianco all'originale e poi lo rinomina al suo posto, così una connessione caduta a metà non può lasciare un file scritto a metà sul server.</p>
            <p>I file si aprono fino a 4 MB, qualunque sia la loro lunghezza: vengono disegnate solo le righe a schermo, quindi un file di megabyte scorre come uno corto. Oltre quella soglia la risposta è il terminale, con <code>nano</code> o <code>vi</code>, che non ha nessun limite.</p>
            <p>I file di configurazione vengono colorati — commenti, stringhe, numeri, chiavi, intestazioni di sezione, variabili di shell e tag XML — in base al nome del file oppure, quando il nome non dice niente, alla forma del contenuto: script di shell, direttive in stile nginx e sshd, INI, YAML, JSON e XML. Il testo libero, le note e i log restano volutamente senza colore, dove sarebbe solo rumore. La colorazione è solo aspetto e non cambia un byte.</p>
            <h3>Editor esadecimale</h3>
            <p>Un file che non è testo si apre nell'editor esadecimale: gli scostamenti a sinistra, i byte al centro, i caratteri stampabili a destra e un tastierino per le cifre esadecimali. Tocca un byte e digita due cifre per sostituirlo. I valori cambiano ma la lunghezza no, quindi ogni posizione nel file resta dov'era. <strong>Apri in esadecimale</strong> nel menu di un file apre così qualunque file, e un file di testo scambiato per binario — per esempio con un byte NUL finito lì per sbaglio — si può comunque aprire come testo dallo stesso dialogo.</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                <ul>
                    <li><strong>Caricare</strong> — apri il menu <strong>Azioni</strong> (⋯) e scegli <em>Carica…</em>; i file si scelgono con il selettore di sistema dell'app File.</li>
                    <li><strong>Dove finiscono i download</strong> — nella cartella di SSHBorg, che l'app File mostra in <em>Su iPhone → SSHBorg</em> (<em>Su iPad</em> su un iPad). Un download non sovrascrive mai nulla: se il nome è già usato, il nuovo file riceve un nome numerato.</li>
                    <li><strong>Cartelle</strong> — tieni premuto un file o una cartella per il suo menu: <em>Scarica</em>, <em>Rinomina</em>, <em>Elimina</em>.</li>
                    <li><strong>Selezione multipla</strong> — scegli <em>Seleziona elementi</em> nel menu Azioni, poi spunta gli elementi. Una pressione prolungata apre invece il menu dell'elemento.</li>
                    <li><strong>Conflitti in caricamento</strong> — la finestra offre <em>Sovrascrivi</em>, <em>Mantieni entrambi</em> (il file caricato riceve un nuovo nome) o <em>Annulla</em>.</li>
                    <li><strong>Modifica</strong> — ci sono anche qui l'editor e quello esadecimale: il menù di un file, con la pressione lunga, offre <em>Apri nell'editor</em> e <em>Apri in esadecimale</em>. Mentre viene caricato, il file passa dalla cartella temporanea dell'app e viene rimosso appena è stato letto.</li>
                    <li><strong>Salvataggio</strong> — il file viene scritto direttamente sull'originale, non su un file temporaneo accanto che poi viene rinominato al suo posto: una connessione che cade a metà salvataggio può quindi lasciarlo scritto a metà.</li>
                </ul>
            </div>`,

  doc_ssh_keys: `
            <h2>// CHIAVI SSH</h2>
            <p>L'autenticazione tramite chiave è più sicura delle password e, una volta configurata, non richiede di ricordare o digitare nulla.</p>
            <h3>Generare una chiave</h3>
            <p>Tocca l'icona della <strong>chiave</strong> in alto nella schermata degli host per aprire <strong>Chiavi SSH</strong>, poi genera una nuova chiave. SSHBorg supporta:</p>
            <ul>
                <li><strong>Ed25519</strong> — consigliata. Veloce, compatta e sicura.</li>
                <li><strong>ECDSA (P-256 / P-384)</strong> — buona compatibilità con server più vecchi.</li>
                <li><strong>RSA (2048 / 4096 bit)</strong> — massima compatibilità, ma più lenta.</li>
            </ul>
            <p>Dai alla chiave un nome significativo (es. <em>mio-vps</em> o <em>server-lavoro</em>) per riconoscerla in seguito.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DI SICUREZZA</div>
                SSHBorg non permette intenzionalmente l'esportazione delle chiavi private. La chiave non lascia mai il dispositivo. Se hai bisogno della stessa chiave su un altro dispositivo, genera una nuova chiave su quel dispositivo e autorizzala separatamente sui tuoi server — è l'approccio più sicuro.
            </div>
            <h3>Autorizzare la chiave sul server</h3>
            <p>Dopo aver generato una chiave, toccala per vedere la <strong>chiave pubblica</strong>. Copiala e incollala nel file <code>~/.ssh/authorized_keys</code> del server per l'utente con cui vuoi accedere.</p>
            <ol class="steps">
                <li>Sul telefono, apri SSHBorg → Chiavi SSH (l'icona della chiave nella schermata degli host) → tocca la chiave → copia la chiave pubblica.</li>
                <li>Accedi al tuo server (con una password, o un'altra chiave già presente).</li>
                <li>Aggiungi la chiave pubblica al file delle chiavi autorizzate:
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...latuachiave..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>Prova a connetterti con SSHBorg — dovrebbe accedere senza chiedere la password.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// REQUISITO SERVER</div>
                Assicurati che il server abbia <code>PubkeyAuthentication yes</code> in <code>/etc/ssh/sshd_config</code>. È il valore predefinito sulla maggior parte delle distribuzioni, ma alcune immagini hardened lo disabilitano.
            </div>
            <h3>Cifrare chiavi e password salvate</h3>
            <p>Attiva <strong>Impostazioni → Sicurezza → Cifra dati sensibili</strong> per conservare le chiavi private e le password degli host cifrate con una chiave custodita nell'hardware sicuro del dispositivo — l'Android Keystore, o il Portachiavi su iOS. Resta disattivata finché non la abiliti; il consiglio di sicurezza mostrato al primo avvio la segnala.</p>
            <p>I dati cifrati sono legati al dispositivo e a questa installazione dell'app: dopo la disinstallazione le chiavi salvate non si possono recuperare, e dovresti generarne di nuove e autorizzarle di nuovo sui server. Insieme a un blocco app, è fortemente consigliata se conservi sul telefono credenziali sensibili.</p>
            <p>Una chiave protetta da passphrase la chiede una volta sola, al momento dell'importazione, e viene poi conservata aperta: la passphrase non viene scritta sul dispositivo. Una passphrase di solito è riusata altrove, mentre una chiave privata vale solo per sé — e una chiave conservata accanto alla propria passphrase non ne sarebbe protetta comunque. Qui la proteggono lo spazio privato dell'app, <strong>Cifra dati sensibili</strong> e il blocco dell'app. Una chiave importata da una versione precedente è rimasta cifrata e non può autenticarsi: nella lista sono segnalate, e vanno importate di nuovo.</p>
            <p>Due formati cifrati che l'app non sa aprire: il contenitore di OpenSSL (<code>BEGIN ENCRYPTED PRIVATE KEY</code>) e il <code>.ppk</code> di PuTTY. L'importazione lo dice, invece di prendere la chiave a metà. Decifrala con lo strumento che l'ha creata e importala di nuovo — lavorando su una copia, non sull'unico file che hai:</p>
            <pre><code>ssh-keygen -p -N "" -f id_ed25519_copy
openssl pkcs8 -in key.pem -out key_plain.pem</code></pre>
            <p>PuTTYgen converte un <code>.ppk</code> da <em>Conversions → Export OpenSSH key</em>.</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                Su iOS funziona allo stesso modo: la passphrase viene chiesta una volta sola, all'importazione, la chiave è conservata aperta e la passphrase non viene scritta da nessuna parte. Rifiuta un formato in più rispetto ad Android: oltre al contenitore di OpenSSL e al <code>.ppk</code> di PuTTY, anche la vecchia cifratura PEM (<code>Proc-Type: 4,ENCRYPTED</code>), quella che <code>ssh-keygen -m PEM</code> scrive quando gli si dà una passphrase. Decifra una chiave così e importala di nuovo.
            </div>`,

  doc_suggestions: `
            <h2>// SUGGERIMENTI COMANDI</h2>
            <p>SSHBorg mostra una barra di suggerimenti sopra la tastiera mentre digiti nel terminale. I suggerimenti vengono presi dalla cronologia della shell dell'utente con cui ti sei connesso.</p>
            <h3>Come funziona</h3>
            <p>Quando si apre una sessione terminale, SSHBorg legge il file di cronologia della shell dal server remoto. Cerca i seguenti percorsi in ordine:</p>
            <ol>
                <li><code>~/.bash_history</code> — predefinito per le shell Bash</li>
                <li><code>~/.zsh_history</code> — predefinito per Zsh (verificato anche come <code>$HISTFILE</code> se impostato)</li>
                <li><code>~/.local/share/fish/fish_history</code> — per gli utenti di Fish shell</li>
            </ol>
            <p>Viene utilizzato il primo file esistente e leggibile. Mentre digiti, i comandi vengono filtrati in tempo reale e mostrati come chip nella barra dei suggerimenti. Tocca un chip per inserire il comando.</p>
            <h3>Risoluzione problemi</h3>
            <p><strong>Nessun suggerimento appare</strong></p>
            <ul>
                <li>Il file di cronologia potrebbe non esistere ancora (primo accesso, o shell non configurata per salvare la cronologia).</li>
                <li>Assicurati che la tua shell salvi la cronologia. Per Bash, aggiungi a <code>~/.bashrc</code>:
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>Per Zsh, aggiungi a <code>~/.zshrc</code>:
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>I suggerimenti appartengono all'utente sbagliato</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// LIMITAZIONE NOTA</div>
                Se ti connetti come un utente e poi esegui <code>sudo su - root</code> (o passi a un altro utente con <code>su</code>), la barra dei suggerimenti mostra ancora la cronologia dell'<em>utente di login originale</em>, non di <code>root</code>. Questo perché SSHBorg legge il file di cronologia prima che la shell avvii, usando le credenziali con cui ti sei connesso.
                <br><br>
                Per ottenere i suggerimenti dalla cronologia di root, aggiungi un host separato in SSHBorg configurato per accedere direttamente come <code>root</code> (se il server lo consente).
            </div>`,

  doc_terminal: `
            <h2>// GESTURE DEL TERMINALE</h2>
            <p>Il terminale risponde ad alcune gesture touch oltre alla digitazione:</p>
            <ul>
                <li><strong>Scorrere la cronologia</strong> — scorri su o giù per navigare nel buffer di scrollback del terminale.</li>
                <li><strong>Zoom</strong> — pizzica per aumentare o diminuire la dimensione del testo.</li>
                <li><strong>Copiare il testo</strong> — tieni premuto in un punto del terminale per entrare in modalità selezione. Trascina i marcatori per regolare l'area selezionata, poi tocca <em>Copia selezione</em> per copiare solo il testo evidenziato, oppure <em>Copia tutto</em> per copiare l'intero output. Tocca altrove per annullare. Su iOS una pressione prolungata seleziona la parola sotto il dito e apre il menu di sistema con <em>Copia</em>, <em>Incolla</em> e <em>Seleziona tutto</em>; trascina da un estremo della selezione per estenderla.</li>
                <li><strong>Incollare</strong> — usa il tasto <em>Incolla</em> nella barra dei tasti extra (visibile quando la tastiera è aperta).</li>
            </ul>
            <p><strong>App a tutto schermo.</strong> I programmi che occupano l'intero schermo — tmux, vim, nano, less — non hanno una cronologia propria, quindi mentre sono in esecuzione la vista resta sulla schermata attiva invece di scivolare nell'output della shell che sta sotto.</p>
            <p>Per scorrere al loro interno deve essere l'applicazione a occuparsene. In tmux aggiungi <code>set -g mouse on</code> in <code>~/.tmux.conf</code>: lo swipe scorre allora la cronologia del pannello sotto al dito. Lo stesso vale per qualsiasi programma che accetti input dal mouse, come vim con <code>set mouse=a</code>. Con <code>screen</code>, verifica che in <code>~/.screenrc</code> ci sia <code>altscreen on</code> — su molti sistemi è disattivato di default, e screen disegna sopra alla normale cronologia.</p>`,

  doc_terminal_settings: `
            <h2>// IMPOSTAZIONI DEL TERMINALE</h2>
            <p>In <strong>Impostazioni → Terminale</strong> puoi adattare il terminale alle tue esigenze:</p>
            <ul>
                <li><strong>Colori del terminale</strong> — lo schema <em>scuro</em> classico (bianco su nero), uno schema <em>chiaro</em> (nero su bianco) più leggibile in piena luce, oppure <em>come il tema dell'app</em>, che cambia automaticamente insieme al tema dell'app. La modifica si applica subito, anche alle sessioni già aperte.</li>
                <li><strong>Mantieni lo schermo acceso</strong> — impedisce lo spegnimento dello schermo mentre un terminale è aperto. Comodo quando osservi log o comandi di lunga durata. Disattivato di default.</li>
                <li><strong>Dimensione carattere predefinita</strong> — la dimensione del testo con cui partono le nuove sessioni; puoi comunque usare il pinch-zoom in ogni sessione.</li>
                <li><strong>Scrollback</strong>, <strong>scorrimento invertito</strong> e <strong>suggerimenti dei comandi</strong> — controllano quanta cronologia di output viene conservata, la direzione dello scorrimento e la barra dei suggerimenti descritta sopra.</li>
                <li><strong>Azione doppio tocco</strong> — se vuoi, un doppio tocco sul terminale invia <em>Tab</em> (autocompletamento) o due volte <em>Tab</em> (elenca tutti i candidati). Disattivato di default. Su iOS, finché è disattivata, un doppio tocco seleziona una parola.</li>
            </ul>`,

  doc_extra_keys: `
            <h2>// BARRA DEI TASTI EXTRA</h2>
            <p>Quando la tastiera virtuale è aperta, sopra di essa appare una riga di pulsanti scorciatoia. Scorri la barra lateralmente per raggiungere tutti i tasti.</p>
            <h3>Tasti modificatori</h3>
            <p><strong>Ctrl</strong> e <strong>Alt</strong> sono toggle persistenti — toccane uno, poi tocca un tasto lettera per inviare la combinazione. Si resettano automaticamente dopo il tasto successivo.</p>
            <ul>
                <li><strong>Ctrl+C</strong> — interrompe il processo in esecuzione.</li>
                <li><strong>Ctrl+D</strong> — invia EOF / chiude la shell.</li>
                <li><strong>Ctrl+Z</strong> — sospende il processo.</li>
                <li><strong>Ctrl+L</strong> — pulisce lo schermo.</li>
            </ul>
            <h3>Modalità parole</h3>
            <p>L'icona di correzione ortografica alterna la tastiera tra <em>modalità terminale</em> e <em>modalità parole</em>. In modalità terminale (predefinita) autocorrettore e suggerimenti di parole sono disabilitati — ideale per comandi e percorsi di file. In modalità parole la tastiera si comporta come un normale campo di testo, con suggerimenti e autocorrettore attivi. Utile quando si digita testo in linguaggio naturale via SSH, ad esempio con Claude Code o altri strumenti interattivi.</p>
            <p>Su Android la modalità parole è anche ciò che fa funzionare la digitazione vocale. Il tasto microfono della tastiera ha bisogno di un vero campo di testo in cui scrivere, quindi in modalità terminale la dettatura non produce nulla — passa alla modalità parole prima di dettare.</p>
            <h3>Navigazione e modifica</h3>
            <ul>
                <li><strong>ESC</strong> — tasto Escape.</li>
                <li><strong>Tab</strong> — autocompletamento della shell.</li>
                <li><strong>↑ ↓ ← →</strong> — frecce direzionali.</li>
                <li><strong>Home / End</strong> — salta all'inizio o alla fine della riga.</li>
                <li><strong>PgUp / PgDn</strong> — pagina su / pagina giù.</li>
                <li><strong>Del</strong> — cancella in avanti (il carattere a destra del cursore).</li>
                <li><strong>Incolla</strong> — incolla gli appunti nel terminale.</li>
            </ul>
            <h3>Tasti funzione</h3>
            <p>Scorri la barra verso destra per raggiungere i tasti <strong>da F1 a F12</strong>.</p>
            <h3>Layout e barre personalizzate</h3>
            <p>La barra esiste in più layout. Tocca il tasto <strong>⇄</strong> sulla barra per cambiarlo: i tasti lasciano il posto all'elenco delle barre disponibili, ne tocchi una e hai finito — la scelta viene ricordata. Puoi scegliere e gestire le barre anche da <strong>Impostazioni → Terminale → Barra tasti extra → Personalizza</strong>.</p>
            <ul>
                <li><strong>Standard</strong> — la classica riga singola a scorrimento.</li>
                <li><strong>Naturale</strong> — una riga ordinata per frequenza d'uso, con <code>/ - | ~</code> e le frecce nell'ordine della tastiera (← ↑ ↓ →).</li>
                <li><strong>Naturale ×2</strong> — due righe che riempiono la larghezza: <code>ESC / - Home ↑ End PgUp</code> sopra <code>Tab Ctrl Alt ← ↓ → PgDn</code>.</li>
                <li><strong>Naturale ×3</strong> — come sopra più una terza riga con i tasti funzione.</li>
                <li><strong>Minimale</strong> — solo ESC, Tab, Ctrl e le frecce, per schermi stretti.</li>
            </ul>
            <p>Per crearne una tua, duplica un preset (menu <strong>⋮</strong> accanto, o una pressione prolungata su iOS) oppure crea una barra nuova. L'editor mostra la barra esattamente come apparirà: tocca un tasto per selezionarlo, poi usa i pulsanti sotto per spostarlo a sinistra o a destra, in un'altra riga, cambiarlo, aggiungere un tasto dopo di lui o rimuoverlo. Ogni riga può <em>scorrere</em> (i tasti tengono la larghezza naturale) oppure <em>riempire</em> la larghezza dello schermo (fino a circa nove tasti per riga su un telefono). Fino a tre righe; la dimensione dei tasti è regolabile.</p>
            <p>Oltre ai tasti soliti, una barra personalizzata può contenere <strong>tasti di testo</strong>: un testo qualsiasi, inviato così com'è — un singolo <code>|</code>, un prefisso <code>sudo </code> o un comando intero. Usa <code>\\n</code> per Invio, <code>\\t</code> per Tab e <code>\\e</code> per Esc, così <code>ls -la\\n</code> diventa una macro a un tocco. Ci sono altri due tasti azione: <strong>puntina</strong> tiene la barra visibile a tastiera chiusa, <strong>tastiera</strong> mostra o nasconde la tastiera.</p>
            <p>Le barre personalizzate sono incluse nel backup delle impostazioni. Su Android TV funziona tutto col telecomando: tasti e pulsanti dell'editor sono raggiungibili col D-pad.</p>`,

  doc_agent_forwarding: `
            <h2>// AGENT FORWARDING</h2>
            <p>L'agent forwarding SSH permette di usare le chiavi memorizzate in SSHBorg per autenticare ulteriori connessioni effettuate <em>dall'interno</em> del server remoto — ad esempio per fare <code>git clone</code> di un repo privato, o per saltare su una seconda macchina.</p>
            <h3>Abilitare il forwarding in SSHBorg</h3>
            <p>Quando aggiungi o modifichi un host, abilita il toggle <strong>Agent forwarding</strong>. SSHBorg fungerà da agente SSH per quella sessione.</p>
            <h3>Configurazione lato server</h3>
            <p>Il server deve permettere l'agent forwarding. Controlla <code>/etc/ssh/sshd_config</code>:</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>È il valore predefinito sulla maggior parte dei sistemi. Dopo averlo modificato, riavvia il demone SSH:</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>Configurazione client per host (opzionale)</h3>
            <p>Se ti connetti a questo server anche da laptop o desktop, puoi configurare il forwarding in modo persistente nel tuo <code>~/.ssh/config</code> locale:</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DI SICUREZZA</div>
                L'agent forwarding dà al server remoto accesso temporaneo al socket del tuo agente SSH. Un utente root (o un processo compromesso) su quel server potrebbe usare le tue chiavi per connettersi altrove mentre la sessione è attiva. Abilita il forwarding solo su server di cui ti fidi.
            </div>`,

  doc_legacy_ciphers: `
            <h2>// CIFRATURE LEGACY</h2>
            <p>Alcuni server più vecchi — apparati di rete, dispositivi embedded o sistemi con versioni obsolete di OpenSSH — supportano solo algoritmi di cifratura che i client SSH moderni non annunciano più di default.</p>
            <p>Abilitando il toggle <strong>Cifrature Legacy</strong> nella configurazione dell'host, SSHBorg aggiunge i seguenti algoritmi alla lista di negoziazione:</p>
            <h3>Algoritmi aggiunti</h3>
            <ul>
                <li><strong>Cifrature:</strong> <code>aes128-cbc</code>, <code>aes192-cbc</code>, <code>aes256-cbc</code>, <code>3des-cbc</code></li>
                <li><strong>Key exchange:</strong> <code>diffie-hellman-group14-sha1</code>, <code>diffie-hellman-group-exchange-sha1</code>, <code>diffie-hellman-group1-sha1</code></li>
                <li><strong>Tipo chiave host:</strong> <code>ssh-dss</code> (DSA 1024-bit)</li>
            </ul>
            <p>Il server negozia sempre l'algoritmo più forte disponibile su entrambi i lati, quindi abilitare questa opzione non indebolisce le connessioni ai server moderni — gli algoritmi legacy vengono usati solo se il server non può offrire nulla di meglio.</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                Su iOS le chiavi host <code>ssh-dss</code> non sono disponibili: la libreria SSH con cui è compilata l'app non include più DSA, quindi un server che ha solo una chiave host DSA non è raggiungibile da iPhone o iPad, nemmeno con questa opzione attiva.
            </div>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DI SICUREZZA</div>
                Gli algoritmi in questo elenco sono considerati crittograficamente deboli. Abilitare questa opzione solo per server che non è possibile aggiornare.
            </div>`,

  doc_connection_drops: `
            <h2>// CONNESSIONI INSTABILI E MULTIPLEXER</h2>
            <p>SSH è una connessione TCP attiva tra il telefono e il server. Se la connessione si interrompe — anche solo per un secondo — la sessione e tutto ciò che stava eseguendo va perso.</p>
            <h3>Perché le connessioni cadono su mobile</h3>
            <p>Le reti mobili sono particolarmente soggette a interruzioni per diversi motivi:</p>
            <ul>
                <li><strong>Cambio di indirizzo IP</strong> — quando sei in viaggio o cambi ripetitore, l'operatore può assegnarti un nuovo IP pubblico. Poiché le connessioni TCP sono legate all'indirizzo IP, la sessione SSH esistente diventa immediatamente non valida.</li>
                <li><strong>Cambio Wi-Fi ↔ dati mobili</strong> — passare da una rete Wi-Fi ai dati mobili (o viceversa) cambia il tuo IP e interrompe qualsiasi connessione TCP aperta.</li>
                <li><strong>Timeout di inattività</strong> — operatori e router NAT spesso chiudono le connessioni inattive dopo pochi minuti. Le sessioni attive ma silenziose (guardare log, aspettare input) sono vulnerabili a questo.</li>
                <li><strong>Perdita di segnale</strong> — tunnel, parcheggi sotterranei o semplicemente un segnale debole possono interrompere brevemente la rete, il che è sufficiente per uccidere una sessione.</li>
                <li><strong>Limiti di Android in background</strong> — per risparmiare batteria, Android limita quanto a lungo un'app può restare attiva in background. Dopo circa sei ore cumulative in background, il sistema interrompe le sessioni di SSHBorg; ricevi una notifica e puoi riconnetterti riaprendo l'app. Riportare l'app in primo piano azzera questo limite.</li>
                <li><strong>iOS sospende le app in background</strong> — circa trenta secondi dopo che SSHBorg lascia lo schermo, iOS lo sospende e le sue connessioni cadono. Quando torni, SSHBorg si riconnette da solo (chiedendo la password solo se non è salvata) e segna il punto nell'output: è una nuova shell, e ciò che girava in quella vecchia è perso. Su iOS un multiplexer è l'unico modo per lasciare un lavoro in esecuzione mentre usi un'altra app.</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                Se stai eseguendo un comando lungo (una build, un backup, una migrazione del database) direttamente nel terminale SSH e la connessione cade, il comando viene interrotto immediatamente. Qualsiasi lavoro parziale potrebbe essere lasciato in uno stato inconsistente.
            </div>
            <h3>La soluzione: tmux o screen</h3>
            <p>Un multiplexer di terminale esegue una sessione persistente <em>sul server</em>, completamente indipendente dalla connessione SSH. Se la connessione cade, la sessione e tutto ciò che sta eseguendo continua. Quando ti riconnetti, ti riattacchi e trovi tutto esattamente com'era.</p>
            <p>È l'abitudine più utile per chi gestisce server dal telefono.</p>
            <h3>Guida rapida a tmux</h3>
            <p><code>tmux</code> è disponibile sulla maggior parte delle distribuzioni Linux moderne ed è la scelta consigliata.</p>
            <pre><code># Avvia una nuova sessione con nome
tmux new -s work

# Distacca dalla sessione (lasciala in esecuzione)
Ctrl+B, poi D

# Elenca le sessioni in esecuzione
tmux ls

# Riattacca a una sessione
tmux attach -t work

# Riattacca alla sessione più recente
tmux attach</code></pre>
            <h3>Guida rapida a screen</h3>
            <p><code>screen</code> è più vecchio ma disponibile su praticamente ogni sistema Unix, incluse le immagini server minimali dove tmux potrebbe non essere installato.</p>
            <pre><code># Avvia una nuova sessione con nome
screen -S work

# Distacca dalla sessione
Ctrl+A, poi D

# Elenca le sessioni in esecuzione
screen -ls

# Riattacca a una sessione
screen -r work</code></pre>
            <h3>Flusso di lavoro consigliato su mobile</h3>
            <ol class="steps">
                <li>Connettiti al server con SSHBorg.</li>
                <li>Avvia o riattacca subito una sessione tmux/screen: <code>tmux attach || tmux new -s main</code></li>
                <li>Esegui i tuoi comandi all'interno del multiplexer.</li>
                <li>Se la connessione cade, riconnettiti — la sessione è ancora lì.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// SUGGERIMENTO</div>
                Puoi aggiungere <code>tmux attach || tmux new -s main</code> al tuo <code>~/.bashrc</code> o <code>~/.zshrc</code> sul server in modo che una sessione multiplexer si avvii automaticamente ogni volta che accedi tramite SSHBorg.
            </div>`,

  doc_jump_hosts: `
            <h2>// JUMP HOST</h2>
            <p>Un jump host (detto anche bastion host) è un server intermedio attraverso cui devi passare per raggiungere un server di destinazione non direttamente accessibile da internet. SSHBorg supporta nativamente catene di salto singole e multi-hop.</p>
            <h3>Configurare un jump host in SSHBorg</h3>
            <ol class="steps">
                <li>Aggiungi il server bastion come host normale in SSHBorg (es. <em>bastion</em>).</li>
                <li>Aggiungi il server di destinazione come altro host.</li>
                <li>Nelle impostazioni dell'host di destinazione, imposta <strong>Jump host</strong> al bastion host creato.</li>
                <li>Abilita <strong>Agent forwarding</strong> sulla voce del bastion — questo permette alla tua chiave di essere inoltrata attraverso il bastion per autenticarsi sulla destinazione.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// COME FUNZIONA</div>
                SSHBorg stabilisce prima una connessione SSH al bastion, poi apre un canale TCP inoltrato attraverso di esso al server di destinazione. La chiave privata non lascia mai il telefono — il bastion fa solo da proxy per il flusso cifrato.
            </div>
            <h3>Catene multi-hop</h3>
            <p>Se devi saltare attraverso più server intermedi (es. internet → bastion → dmz → target), crea una voce per ogni hop e concatenale:</p>
            <ul>
                <li><strong>bastion</strong> — nessun jump host, agent forwarding attivo</li>
                <li><strong>dmz</strong> — jump host = bastion, agent forwarding attivo</li>
                <li><strong>target</strong> — jump host = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                L'agent forwarding deve essere abilitato su <em>ogni hop intermedio</em>, non solo sul primo. Senza di esso, la catena di autenticazione si interrompe e la connessione al server finale fallirà con un errore "permission denied".
            </div>
            <h3>Configurazione manuale equivalente (per riferimento)</h3>
            <p>La configurazione equivalente in un <code>~/.ssh/config</code> su desktop è la seguente:</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>Con questa configurazione, <code>ssh target</code> sul laptop salta trasparentemente attraverso il bastion.</p>
            <h3>Requisiti del firewall</h3>
            <ul>
                <li>Il telefono deve poter raggiungere il bastion sulla sua porta SSH (di solito 22).</li>
                <li>Il bastion deve poter raggiungere il target sulla sua porta SSH.</li>
                <li>Il target <em>non</em> deve essere raggiungibile direttamente dal telefono.</li>
            </ul>`,

  doc_sessions: `
            <h2>// SESSIONI MULTIPLE</h2>
            <p>SSHBorg permette di tenere aperte più sessioni di terminale SSH e di file manager SFTP contemporaneamente, anche verso server diversi.</p>
            <ul>
                <li>Tocca un host per aprire un terminale, oppure scegli <strong>File</strong> nel suo menu per aprire il file manager. Se l'host ha già sessioni aperte, lo stesso menu ne apre altre.</li>
                <li>Passa da una sessione all'altra con le schede sotto il terminale; un host con più sessioni apre un selettore numerato.</li>
                <li>Le sessioni rimangono attive in background finché la connessione di rete regge — su iOS solo per poco, vedi <a href="#connection-drops">Connessioni instabili</a>.</li>
                <li>L'elenco degli host mostra un piccolo badge accanto a ogni host con il numero di sessioni SSH e SFTP attive, così puoi vedere a colpo d'occhio cosa è aperto.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// SUGGERIMENTO</div>
                I comandi a lunga esecuzione (build, backup, tail di log) continuano a girare anche quando passi a un'altra sessione. Usa un multiplexer di terminale come <code>tmux</code> o <code>screen</code> lato server se vuoi che sopravvivano anche a una caduta della connessione SSH.
            </div>`,

  doc_security: `
            <h2>// SICUREZZA DELL'APP</h2>
            <h3>Blocco app</h3>
            <p>Scegli come proteggere l'app in <strong>Impostazioni → Sicurezza → Blocco app</strong>: <strong>Nessuno</strong> (predefinito), <strong>Solo biometrico</strong> (impronta digitale o riconoscimento facciale), <strong>Blocco del dispositivo</strong> (il PIN, la sequenza o la password del dispositivo, oltre alla biometria) o un <strong>PIN o passphrase</strong> in-app. Quando un blocco è attivo, SSHBorg richiede l'autenticazione prima di mostrare qualsiasi host, credenziale o dato di sessione.</p>
            <p>Il <strong>PIN o passphrase</strong> in-app funziona su qualsiasi dispositivo, anche senza hardware biometrico o un blocco schermo di sistema — per questo è la scelta giusta su una Android TV. Per cambiarlo o rimuoverlo viene chiesto prima quello attuale. Non è recuperabile se dimenticato: dovresti cancellare i dati dell'app o reinstallarla, quindi tieni un backup dei tuoi host.</p>
            <p>Puoi impostare un timeout di inattività — dopo quel numero di minuti in background l'app si blocca automaticamente.</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                Il blocco offre <strong>Nessuno</strong>, <strong>Solo biometrico</strong> (Face ID o Touch ID) e <strong>Blocco del dispositivo</strong> (il codice del dispositivo o la biometria). Non esiste un PIN o una passphrase in-app.
            </div>
            <h3>Protezione screenshot</h3>
            <p>Per impostazione predefinita SSHBorg blocca screenshot e registrazione dello schermo per evitare che il contenuto sensibile del terminale trapeli tramite la schermata delle app recenti o strumenti di cattura dello schermo.</p>
            <p>Se devi fare uno screenshot (es. per condividere un output del terminale), puoi disabilitare temporaneamente la protezione in <strong>Impostazioni → Sicurezza → Consenti screenshot</strong>.</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                iOS non permette a un'app di bloccare screenshot o registrazioni dello schermo, quindi questa impostazione non esiste. Con un blocco app attivo, SSHBorg copre i propri contenuti appena lascia lo schermo, così host e terminali non compaiono nel multitasking.
            </div>
            <h3>Archiviazione delle credenziali</h3>
            <p>Con <strong>Cifra dati sensibili</strong> attiva (vedi <a href="#ssh-keys">Chiavi SSH</a>), password e chiavi private sono conservate cifrate con una chiave custodita nell'<strong>Android Keystore</strong> — basato su hardware su Android 10+ — o, su iOS, nel <strong>Portachiavi</strong>, limitata a questo dispositivo. Le credenziali non vengono mai scritte su storage esterno né trasmesse da nessuna parte.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA SUL BACKUP</div>
                Poiché la chiave di cifratura non lascia mai l'hardware sicuro del dispositivo, le chiavi <strong>non possono essere ripristinate</strong> da un backup cloud o del dispositivo e non si trasferiranno automaticamente su un nuovo telefono. Prima di cambiare dispositivo, assicurati di autorizzare una nuova chiave generata sul nuovo dispositivo su tutti i tuoi server.
            </div>
            <h3>L'impostazione più sicura</h3>
            <p>Ognuno dei pezzi qui sopra serve già da solo. Messi insieme sono il massimo che l'app può offrire, e sono due minuti di lavoro:</p>
            <ol>
                <li>Attiva <strong>Impostazioni → Sicurezza → Cifra dati sensibili</strong>.</li>
                <li>Imposta il blocco dell'app su <strong>Solo biometrico</strong> — oppure su <strong>PIN o passphrase</strong> se preferisci non affidarti alla biometria del telefono.</li>
                <li>Imposta il timeout del blocco su <strong>Immediatamente</strong>, o al massimo <strong>30 secondi</strong>.</li>
                <li>Genera una nuova chiave nell'app, con un nome che dica di quale dispositivo è.</li>
                <li>Autorizza quella chiave sui tuoi server e usala invece della password.</li>
            </ol>
            <p>Una chiave generata sul telefono non è mai esistita altrove: non c'è una copia più vecchia che possa già essere sfuggita, e non c'è niente da ripulire su un'altra macchina. Una chiave per dispositivo significa anche che perdere il telefono costa una riga in <code>authorized_keys</code> — la togli e gli altri dispositivi continuano a funzionare, ed è per questo che vale la pena scegliere bene il nome.</p>
            <p>Quello che niente di tutto questo può fare: se il telefono stesso è compromesso, quella chiave è compromessa con lui, qualunque cosa la protegga su disco. Nemmeno una passphrase sulla chiave la salverebbe — chi riesce a leggere lo spazio dell'app riesce anche a leggere quello che digiti. È per questo che SSHBorg chiede la passphrase di una chiave una volta sola, all'importazione, e conserva la chiave aperta invece di tenere la passphrase (vedi <a href="#ssh-keys">Chiavi SSH</a>).</p>`,

  doc_android_tv: `
            <h2>// ANDROID TV</h2>
            <p>SSHBorg funziona su Android TV e Google TV. Si usa con il D-pad del telecomando, ma trattandosi di un'app molto basata sul testo, una tastiera fisica fa una grande differenza.</p>
            <h3>Navigare col telecomando</h3>
            <ul>
                <li><strong>Frecce del D-pad</strong> — spostano il focus tra righe e controlli.</li>
                <li><strong>OK (centrale)</strong> — l'azione primaria: connettere un host, aprire una cartella, scaricare un file, espandere un gruppo.</li>
                <li><strong>Indietro</strong> — sale di livello o esce dalla schermata.</li>
            </ul>
            <h3>Aprire il menu di una riga</h3>
            <p>Per rinominare, eliminare, modificare, duplicare o scaricare da una riga, apri il suo menu: premi il <strong>tasto Menu (opzioni)</strong> del telecomando sulla riga con il focus, oppure <strong>tieni premuto OK</strong> (pressione lunga). OK da solo esegue l'azione primaria, non il menu.</p>
            <h3>Scrivere: usa una tastiera fisica</h3>
            <p>Una <strong>tastiera fisica — USB o Bluetooth</strong> (Android TV supporta entrambe) è fortemente consigliata, e di fatto necessaria per il terminale. Con una tastiera i campi di testo e la shell funzionano normalmente.</p>
            <p>Senza, la tastiera a schermo gestisce comunque inserimenti brevi: metti il focus su un campo, premi OK per aprirlo, digita e premi <strong>OK / Vai</strong> sulla tastiera per confermare — nel prompt della password questo connette direttamente. Inserire comandi shell con la tastiera a schermo, però, è poco pratico.</p>
            <h3>Bloccare l'app su una TV</h3>
            <p>Una TV di solito non ha lettore di impronte né blocco schermo, quindi proteggi l'app con il blocco integrato con <strong>PIN o passphrase</strong> (Impostazioni → Sicurezza) — funziona interamente col telecomando o una tastiera. Vedi <a href="#security">Sicurezza dell'app</a>.</p>`,

  doc_licenses: `
            <h2>// LICENZE OPEN SOURCE</h2>
            <p>SSHBorg è software libero, rilasciato sotto <strong>GNU General Public License v3</strong>. Il codice sorgente è su GitHub, sia per l'app Android sia per quella iOS. Usa le librerie seguenti, ciascuna con la sua licenza:</p>
            <ul>
                <li><a href="https://github.com/mwiede/jsch" target="_blank" rel="noopener"><strong>mwiede/JSch</strong></a> — il protocollo SSH su Android — licenza di tipo BSD</li>
                <li><a href="https://www.bouncycastle.org" target="_blank" rel="noopener"><strong>Bouncy Castle</strong></a> — la crittografia su Android — licenza di tipo MIT</li>
                <li><a href="https://github.com/Rosemoe/sora-editor" target="_blank" rel="noopener"><strong>sora-editor</strong></a> — il componente di modifica testo usato per modificare i file sul server, su Android — GNU LGPL v2.1</li>
                <li><a href="https://libssh2.org" target="_blank" rel="noopener"><strong>libssh2</strong></a> — il protocollo SSH su iOS — licenza BSD</li>
            </ul>
            <p>Nessuna di esse si collega a niente per conto suo: gli unici indirizzi che SSHBorg contatta sono i server che gli indichi tu, e sshborg.com quando tocchi un collegamento. Non ci sono librerie di statistiche, di pubblicità né tracciatori di alcun tipo.</p>`,

  doc_backup: `
            <h2>// BACKUP CONFIGURAZIONE</h2>
            <p>SSHBorg può esportare e importare le configurazioni degli host e le impostazioni dell'app come file JSON. Questo permette di trasferire la lista dei server su un altro dispositivo o di conservare un backup portabile della propria configurazione.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                Il backup include le configurazioni degli host e le impostazioni dell'app (terminale, aspetto, comportamento). <strong>Password e chiavi SSH non vengono mai esportate</strong> — il materiale della chiave va riconfigurato su un nuovo dispositivo. Il backup registra però il <em>nome</em> della chiave usata da ogni host: se ricrei una chiave con lo stesso nome prima di importare, i suoi host vengono ricollegati automaticamente.
            </div>
            <h3>Esportare</h3>
            <p>Vai in <strong>Impostazioni → Backup → Esporta backup</strong>. Scegli dove salvare il file tramite il selettore file di sistema. Il file si chiama <code>sshborg_backup.json</code> (<code>sshborg-backup-</code><em>data</em><code>.json</code> su iOS) per impostazione predefinita.</p>
            <h3>Importare</h3>
            <p>Vai in <strong>Impostazioni → Backup → Importa backup</strong>. Seleziona il file <code>.json</code> precedentemente esportato (o creato manualmente). SSHBorg lo unirà alla lista degli host esistenti:</p>
            <ul>
                <li>Gli host il cui <strong>nome</strong> corrisponde a un host esistente vengono <strong>aggiornati</strong>.</li>
                <li>Gli host con un nome nuovo vengono <strong>aggiunti</strong>.</li>
                <li>Gli host non presenti nel file rimangono <strong>invariati</strong>.</li>
                <li>Un host aggiornato mantiene password, chiave e chiave host accettata già salvate — il backup non le contiene.</li>
                <li>Un host senza chiave viene collegato a una chiave il cui nome corrisponde al <code>keyLabel</code> esportato, se esiste; altrimenti il campo viene ignorato.</li>
                <li>Un host aggiornato mantiene i dati d'uso già presenti su questo dispositivo — ultima connessione, contatore e posizione manuale; il file riempie solo ciò che manca.</li>
</ul>
            <h3>Formato JSON</h3>
            <p>Il file esportato è un normale oggetto JSON. È possibile crearlo manualmente per importare in blocco una lista di server da un'altra fonte.</p>
            <pre><code>{
  "version": 7,
  "exported_at": "2026-05-14T10:00:00Z",
  "groups": [
    { "name": "Produzione", "color": -1754827, "position": 0 }
  ],
  "hosts": [
    {
      "label":           "Il mio VPS",
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
      "sftpShowHidden":  false,
      "allowLegacyCiphers": false,
      "position":        0,
      "lastConnected":   1757404800000,
      "connectCount":    12,
      "keyLabel":         "Chiave VPS",
      "group":           "Produzione"
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
            <p>L'oggetto <code>settings</code> (facoltativo) contiene le impostazioni dell'app e viene scritto automaticamente all'esportazione; puoi ometterlo se crei il file a mano.</p>
            <h3>Riferimento campi</h3>
            <ul>
                <li><code>label</code> — nome visualizzato in SSHBorg. Usato come chiave univoca per l'unione in fase di importazione. <strong>Obbligatorio.</strong></li>
                <li><code>hostname</code> — indirizzo o IP del server (IPv4 o IPv6). <strong>Obbligatorio.</strong></li>
                <li><code>port</code> — porta SSH. Valore predefinito: <code>22</code>.</li>
                <li><code>username</code> — utente di login. <strong>Obbligatorio.</strong></li>
                <li><code>agentForwarding</code> — <code>true</code> per abilitare il forwarding dell'agente SSH. Valore predefinito: <code>false</code>.</li>
                <li><code>jumpMode</code> — <code>"simple"</code> (usa il testo di <code>jumpHosts</code>) o <code>"host_list"</code> (usa gli ID interni di SSHBorg). Usa <code>"simple"</code> quando crei il file manualmente.</li>
                <li><code>jumpHosts</code> — lista di jump host separati da virgola nel formato <code>[utente@]host[:porta]</code>. Usato solo con <code>jumpMode</code> <code>"simple"</code>.</li>
                <li><code>portForwardings</code> — regole di port forwarding locale separate da newline in sintassi SSH <code>-L</code>, es. <code>"8080:localhost:8080"</code>.</li>
                <li><code>sftpStartMode</code> — cartella iniziale SFTP: <code>"last"</code> (ricorda l'ultima visitata), <code>"fixed"</code> (usa sempre <code>sftpStartDir</code>), <code>"home"</code> (home del server). Valore predefinito: <code>"last"</code>.</li>
                <li><code>sftpStartDir</code> — percorso da usare quando <code>sftpStartMode</code> è <code>"fixed"</code>.</li>
                <li><code>sftpShowHidden</code> — <code>true</code> per mostrare i file nascosti (dotfile, nomi che iniziano con ".") nel browser SFTP di questo host. Predefinito <code>false</code>.</li>
                <li><code>allowLegacyCiphers</code> — <code>true</code> per abilitare gli algoritmi legacy descritti sopra. Predefinito: <code>false</code>.</li>
                <li><code>keyLabel</code> — nome della chiave SSH usata da questo host. All'importazione, se esiste una chiave con questo nome viene collegata all'host; altrimenti il campo viene ignorato. La chiave stessa non è mai inclusa nel backup.</li>
                <li><code>group</code> — nome del gruppo a cui appartiene l'host. I gruppi sono elencati nell'array <code>groups</code> in cima al file, con <code>name</code> e <code>color</code> (ARGB come intero a 32 bit con segno). Se un host fa riferimento a un gruppo assente dall'array, il gruppo viene creato automaticamente con un colore predefinito: scrivendo il file a mano puoi quindi omettere l'array.</li>
                <li><code>color</code> — colore opzionale del singolo host (ARGB come intero a 32 bit con segno). Prevale sul colore del gruppo.</li>
                            <li><code>position</code> — posto dell'host nell'ordine manuale, contato dentro la sua sezione (il blocco senza gruppo, oppure un gruppo). Se lo ometti l'host viene accodato in fondo. Anche i gruppi hanno una loro <code>position</code> nell'array <code>groups</code>.</li>
                <li><code>lastConnected</code> — momento dell'ultima connessione, in millisecondi dall'epoca Unix. Alimenta l'ordinamento "recenti".</li>
                <li><code>connectCount</code> — quante sessioni di terminale sono state aperte verso questo host. Alimenta l'ordinamento "più usati".</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// CAMPI OPZIONALI</div>
                Tutti i campi eccetto <code>label</code>, <code>hostname</code> e <code>username</code> sono opzionali. I campi omessi riprendono i valori predefiniti.
            </div>`,

  // ── changelog.html ─────────────────────────────────────────────────────────
  nav_changelog:               'Changelog',
  page_title_changelog:        'SSHBorg – Changelog',
  meta_description_changelog:  'Storico delle versioni di SSHBorg per {PLATFORM}: cosa è cambiato in ogni versione.',
  changelog_subtitle:          'Tutte le versioni pubblicate, dalla più recente.',
};

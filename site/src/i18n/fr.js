'use strict';
module.exports = {
  page_title:        'SSHBorg – Client SSH et SFTP pour Android',
  meta_description:  'SSHBorg est un puissant client SSH et SFTP pour Android. Gérez vos serveurs en toute sécurité depuis votre téléphone avec l\'authentification par clé, le stockage chiffré et le verrouillage biométrique.',

  nav_features:  'Fonctionnalités',
  nav_security:  'Sécurité',
  nav_guide:     'Guide utilisateur',
  nav_support:   'Support',
  nav_privacy:   'Confidentialité',
  nav_tip:       'Laisser un pourboire',
  nav_contact:   'Contact',

  hero_sub: 'Un puissant client SSH &amp; SFTP pour Android.<br>Gérez vos serveurs en toute sécurité, directement depuis votre téléphone.',

  badge_no_ads:      'Sans publicité',
  badge_no_tracking: 'Sans traçage',
  badge_no_cloud:    'Sans cloud',
  badge_free:        'Gratuit',
  badge_android:     'Android 10+',

  cta_play: 'Télécharger sur Google Play',

  features_title: '// FONCTIONNALITÉS',

  feat_terminal_title: 'TERMINAL SSH COMPLET',
  feat_terminal_desc:  'Terminal interactif avec émulation VT100/xterm, support UTF-8 complet et sessions simultanées multiples.',
  feat_sftp_title:     'GESTIONNAIRE DE FICHIERS SFTP',
  feat_sftp_desc:      'Parcourez, envoyez, téléchargez, renommez et supprimez des fichiers sur vos serveurs avec un gestionnaire de fichiers intuitif.',
  feat_keys_title:     'AUTH PAR CLÉ SSH',
  feat_keys_desc:      'Générez des clés Ed25519, ECDSA et RSA directement sur votre appareil. Sans mot de passe.',
  feat_jump_title:     'SUPPORT JUMP HOST',
  feat_jump_desc:      'Connectez-vous via un ou plusieurs hôtes bastions avec tunneling transparent. Transfert complet de l\'agent SSH.',
  feat_biometric_title:'VERROUILLAGE BIOMÉTRIQUE',
  feat_biometric_desc: 'Protégez l\'accès à vos serveurs par empreinte digitale ou reconnaissance faciale. Délai configurable.',
  feat_multilingual_title: 'MULTILINGUE',
  feat_multilingual_desc:  'Disponible en anglais, italien, français, allemand, espagnol, portugais et ukrainien.',
  feat_theme_title:    'THÈME SOMBRE &amp; CLAIR',
  feat_theme_desc:     'Suit le thème système ou vous laisse choisir. Parfaitement lisible dans toutes les conditions d\'éclairage.',
  feat_sessions_title: 'SESSIONS MULTIPLES',
  feat_sessions_desc:  'Gardez plusieurs sessions SSH et SFTP ouvertes simultanément. Basculez entre elles instantanément.',

  security_title: '// VIE PRIVÉE BY DESIGN',
  security_desc:  'SSHBorg ne collecte jamais vos données. Tout reste sur votre appareil — identifiants, clés, connexions.',

  sec_badge_keystore:   'Chiffrement Android Keystore',
  sec_badge_analytics:  'Sans analytics',
  sec_badge_sdks:       'Sans SDK tiers',
  sec_badge_screenshots:'Protection captures d\'écran',
  sec_badge_opensource: 'Bibliothèques open source uniquement',

  security_pp_link: 'Lire la politique de confidentialité complète &rarr;',

  tip_title:  '// LAISSER UN POURBOIRE',
  tip_desc:   'SSHBorg est gratuit, sans publicité et sans traçage. S\'il vous fait gagner du temps, un petit pourboire aide à le maintenir.',
  kofi_cta:        'Me soutenir sur Ko-fi',
  kofi_hero_cta:   'Laissez-moi un pourboire',

  footer_privacy: 'Confidentialité',
  footer_issues:  'Bugs &amp; Retours',
  footer_powered: 'Connectivité SSH fournie par',

  page_title_docs:       'SSHBorg – Guide utilisateur',
  meta_description_docs: 'Guide opérationnel SSHBorg : clés SSH, jump hosts, transfert d\'agent, suggestions de commandes, tmux et plus encore.',

  nav_home:           'Accueil',
  nav_getting_started:'Premiers pas',
  nav_ssh_keys:       'Clés SSH',
  nav_jump_hosts:     'Jump Hosts',
  nav_sftp:           'SFTP',

  doc_page_title:    '// GUIDE UTILISATEUR',
  doc_page_subtitle: 'Guide opérationnel — quoi faire, étape par étape, pour tirer le meilleur parti de SSHBorg.',

  toc_title: '// SOMMAIRE',

  doc_toc: `            <li><a href="#adding-host">Ajouter un hôte</a></li>
            <li><a href="#sftp">Gestionnaire SFTP</a></li>
            <li class="sub"><a href="#sftp">Navigation</a></li>
            <li class="sub"><a href="#sftp">Envoyer et télécharger</a></li>
            <li class="sub"><a href="#sftp">Sélection multiple</a></li>
            <li><a href="#ssh-keys">Clés SSH</a></li>
            <li class="sub"><a href="#ssh-keys">Générer une clé</a></li>
            <li class="sub"><a href="#ssh-keys">Autoriser sur le serveur</a></li>
            <li class="sub"><a href="#ssh-keys">Sécurité des clés</a></li>
            <li><a href="#suggestions">Suggestions de commandes</a></li>
            <li class="sub"><a href="#suggestions">Fonctionnement</a></li>
            <li class="sub"><a href="#suggestions">Résolution de problèmes</a></li>
            <li><a href="#agent-forwarding">Transfert d'agent</a></li>
            <li><a href="#connection-drops">Coupures de connexion</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">Jump Hosts</a></li>
            <li class="sub"><a href="#jump-hosts">Chaînes multi-sauts</a></li>
            <li><a href="#sessions">Sessions multiples</a></li>
            <li><a href="#security">Sécurité de l'app</a></li>`,

  doc_adding_host: `
            <h2>// AJOUTER UN HÔTE</h2>
            <p>Appuyez sur le bouton <strong>+</strong> dans l'écran des hôtes pour ajouter un nouveau serveur.</p>
            <h3>Champs obligatoires</h3>
            <ul>
                <li><strong>Hostname / IP</strong> — l'adresse ou l'IP du serveur. IPv4 et IPv6 sont tous deux pris en charge.</li>
                <li><strong>Port</strong> — par défaut 22. Modifiez-le si votre serveur utilise un port SSH différent.</li>
                <li><strong>Nom d'utilisateur</strong> — l'utilisateur Unix avec lequel vous souhaitez vous connecter (ex. <code>ubuntu</code>, <code>root</code>, <code>deploy</code>).</li>
                <li><strong>Authentification</strong> — choisissez entre mot de passe ou clé SSH (recommandé).</li>
            </ul>
            <h3>Vérification de l'empreinte de l'hôte</h3>
            <p>Lors de la première connexion, SSHBorg affiche l'empreinte du serveur et vous demande de l'accepter. C'est une vérification de sécurité : elle garantit que vous vous connectez à la bonne machine et non à un imposteur. Vérifiez que l'empreinte correspond à celle fournie par votre administrateur avant d'accepter.</p>
            <p>Une fois acceptée, l'empreinte est stockée localement. Si elle change lors d'une connexion future, SSHBorg vous avertira — cela pourrait indiquer une reconstruction du serveur, une rotation des clés ou une attaque man-in-the-middle.</p>
            <div class="callout callout-info">
                <div class="callout-label">// ASTUCE</div>
                Vous pouvez vérifier l'empreinte du serveur à tout moment avec :
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_sftp: `
            <h2>// GESTIONNAIRE DE FICHIERS SFTP</h2>
            <p>Le gestionnaire de fichiers SFTP vous permet de parcourir, envoyer, télécharger, renommer et supprimer des fichiers sur votre serveur directement depuis votre téléphone. Ouvrez une session SFTP depuis l'écran des hôtes en appuyant sur <strong>SFTP</strong>.</p>
            <h3>Navigation</h3>
            <p>Appuyez sur un dossier pour l'ouvrir. Utilisez la flèche de retour ou appuyez sur un segment de la barre de chemin pour remonter dans l'arborescence.</p>
            <p>Les liens symboliques sont affichés avec un petit badge en forme de chaîne. Appuyer sur un lien symbolique navigue vers sa cible : s'il pointe vers un répertoire, vous y entrez ; s'il pointe vers un fichier, il se comporte comme un fichier normal.</p>
            <h3>Envoyer des fichiers</h3>
            <p>Appuyez sur le bouton d'<strong>envoi</strong> (↑) pour sélectionner un ou plusieurs fichiers depuis le stockage de votre téléphone. L'envoi commence immédiatement et la progression est affichée en haut de l'écran.</p>
            <h3>Télécharger des fichiers et des dossiers</h3>
            <p>Appuyez sur un fichier pour le télécharger immédiatement. Pour télécharger un dossier entier, appuyez sur l'icône de <strong>téléchargement</strong> à côté — SSHBorg télécharge toute l'arborescence et la sauvegarde dans le dossier <strong>Téléchargements</strong> de votre téléphone.</p>
            <p>Si un fichier existe déjà à la destination, une boîte de dialogue vous demandera si vous souhaitez <strong>écraser</strong>, <strong>ignorer</strong> le fichier ou <strong>annuler</strong> tout le transfert.</p>
            <div class="callout callout-info">
                <div class="callout-label">// NOTE SUR LES LIENS SYMBOLIQUES</div>
                Lors du téléchargement d'un dossier, les liens symboliques pointant vers des répertoires sont ignorés — seuls les fichiers normaux (y compris les liens vers des fichiers) sont téléchargés. Cela évite les téléchargements récursifs non souhaités.
            </div>
            <h3>Sélection multiple et opérations groupées</h3>
            <p>Appuyez longuement sur un élément pour activer le mode de sélection, puis appuyez sur d'autres éléments pour les ajouter à la sélection. La barre d'outils affiche les actions disponibles :</p>
            <ul>
                <li><strong>Télécharger</strong> — télécharge tous les fichiers et dossiers sélectionnés en une seule fois, avec une boîte de dialogue de progression et la possibilité d'annuler.</li>
                <li><strong>Supprimer</strong> — supprime tous les éléments sélectionnés. La suppression d'un dossier non vide efface tout son contenu de manière récursive. <em>Cette action est irréversible.</em></li>
            </ul>`,

  doc_ssh_keys: `
            <h2>// CLÉS SSH</h2>
            <p>L'authentification par clé est plus sécurisée que les mots de passe et ne nécessite rien à mémoriser ni à saisir après la configuration.</p>
            <h3>Générer une clé</h3>
            <p>Allez dans <strong>Paramètres → Clés SSH → Générer une nouvelle clé</strong>. SSHBorg prend en charge :</p>
            <ul>
                <li><strong>Ed25519</strong> — recommandé. Rapide, compact et sécurisé.</li>
                <li><strong>ECDSA (P-256 / P-384)</strong> — bonne compatibilité avec les anciens serveurs.</li>
                <li><strong>RSA (2048 / 4096 bits)</strong> — compatibilité maximale, mais plus lent.</li>
            </ul>
            <p>Donnez à la clé un nom significatif (ex. <em>mon-vps</em> ou <em>serveur-travail</em>) pour la retrouver facilement.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTE DE SÉCURITÉ</div>
                SSHBorg n'autorise intentionnellement pas l'export des clés privées. La clé ne quitte jamais l'appareil. Si vous avez besoin de la même clé sur un autre appareil, générez-en une nouvelle là-bas et autorisez-la séparément sur vos serveurs — c'est l'approche la plus sûre.
            </div>
            <h3>Autoriser la clé sur le serveur</h3>
            <p>Après avoir généré une clé, appuyez dessus pour voir la <strong>clé publique</strong>. Copiez-la et collez-la dans le fichier <code>~/.ssh/authorized_keys</code> du serveur pour l'utilisateur souhaité.</p>
            <ol class="steps">
                <li>Sur votre téléphone, ouvrez SSHBorg → Paramètres → Clés SSH → appuyez sur la clé → copiez la clé publique.</li>
                <li>Connectez-vous à votre serveur (avec un mot de passe ou une autre clé déjà disponible).</li>
                <li>Ajoutez la clé publique au fichier des clés autorisées :
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...votreclé..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>Essayez de vous connecter avec SSHBorg — il devrait se connecter sans demander de mot de passe.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// PRÉREQUIS SERVEUR</div>
                Assurez-vous que votre serveur a <code>PubkeyAuthentication yes</code> dans <code>/etc/ssh/sshd_config</code>. C'est la valeur par défaut sur la plupart des distributions, mais certaines images renforcées le désactivent.
            </div>
            <h3>Chiffrement supplémentaire de la clé</h3>
            <p>SSHBorg propose une <strong>phrase secrète supplémentaire</strong> optionnelle pour vos clés (Paramètres → Clés SSH → appuyez sur une clé → Activer le chiffrement). Quand elle est activée, la clé est chiffrée avec une phrase que SSHBorg ne stocke pas — elle vous sera demandée à chaque utilisation.</p>
            <p>Fortement recommandé si vous stockez des identifiants sensibles sur votre téléphone ou si le verrouillage biométrique est désactivé.</p>`,

  doc_suggestions: `
            <h2>// SUGGESTIONS DE COMMANDES</h2>
            <p>SSHBorg affiche une barre de suggestions au-dessus du clavier pendant que vous tapez dans le terminal. Les suggestions proviennent de l'historique du shell de l'utilisateur avec lequel vous êtes connecté.</p>
            <h3>Fonctionnement</h3>
            <p>Lors de l'ouverture d'une session terminal, SSHBorg lit le fichier d'historique du shell sur le serveur distant. Il vérifie les emplacements suivants dans l'ordre :</p>
            <ol>
                <li><code>~/.bash_history</code> — par défaut pour les shells Bash</li>
                <li><code>~/.zsh_history</code> — par défaut pour Zsh (aussi vérifié comme <code>$HISTFILE</code> si défini)</li>
                <li><code>~/.local/share/fish/fish_history</code> — pour les utilisateurs de Fish shell</li>
            </ol>
            <p>Le premier fichier existant et lisible est utilisé. Pendant la saisie, les commandes sont filtrées en temps réel et affichées sous forme de puces dans la barre de suggestions. Appuyez sur une puce pour insérer la commande.</p>
            <h3>Résolution de problèmes</h3>
            <p><strong>Aucune suggestion n'apparaît</strong></p>
            <ul>
                <li>Le fichier d'historique n'existe peut-être pas encore (première connexion ou shell non configuré pour sauvegarder l'historique).</li>
                <li>Assurez-vous que votre shell est configuré pour enregistrer l'historique. Pour Bash, ajoutez à <code>~/.bashrc</code> :
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>Pour Zsh, ajoutez à <code>~/.zshrc</code> :
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>Les suggestions appartiennent au mauvais utilisateur</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// LIMITATION CONNUE</div>
                Si vous vous connectez en tant qu'un utilisateur puis exécutez <code>sudo su - root</code> (ou changez d'utilisateur avec <code>su</code>), la barre de suggestions affiche toujours l'historique de l'<em>utilisateur de connexion d'origine</em>, pas de <code>root</code>. Cela s'explique par le fait que SSHBorg lit le fichier d'historique avant le démarrage du shell, en utilisant les identifiants de connexion.
                <br><br>
                Pour obtenir les suggestions de l'historique root, ajoutez une entrée d'hôte séparée dans SSHBorg configurée pour se connecter directement en tant que <code>root</code> (si votre serveur le permet).
            </div>`,

  doc_agent_forwarding: `
            <h2>// TRANSFERT D'AGENT</h2>
            <p>Le transfert d'agent SSH permet d'utiliser les clés stockées dans SSHBorg pour authentifier des connexions supplémentaires effectuées <em>depuis</em> le serveur distant — par exemple pour <code>git clone</code> d'un dépôt privé, ou pour rebondir vers une deuxième machine.</p>
            <h3>Activer le transfert dans SSHBorg</h3>
            <p>Lors de l'ajout ou de la modification d'un hôte, activez le commutateur <strong>Transfert d'agent</strong>. SSHBorg agira comme agent SSH pour cette session.</p>
            <h3>Configuration côté serveur</h3>
            <p>Le serveur doit autoriser le transfert d'agent. Vérifiez <code>/etc/ssh/sshd_config</code> :</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>C'est la valeur par défaut sur la plupart des systèmes. Après modification, redémarrez le démon SSH :</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>Configuration client par hôte (optionnel)</h3>
            <p>Si vous vous connectez aussi à ce serveur depuis un ordinateur portable ou de bureau, vous pouvez configurer le transfert de façon permanente dans votre <code>~/.ssh/config</code> local :</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTE DE SÉCURITÉ</div>
                Le transfert d'agent donne au serveur distant un accès temporaire à votre socket d'agent SSH. Un utilisateur root (ou un processus compromis) sur ce serveur pourrait utiliser vos clés pour se connecter ailleurs pendant que votre session est active. N'activez le transfert que sur les serveurs de confiance.
            </div>`,

  doc_connection_drops: `
            <h2>// COUPURES DE CONNEXION ET MULTIPLEXEURS</h2>
            <p>SSH est une connexion TCP active entre votre téléphone et le serveur. Si la connexion est interrompue — même une seconde — la session et tout ce qui s'y exécutait est perdu.</p>
            <h3>Pourquoi les connexions se coupent sur mobile</h3>
            <p>Les réseaux mobiles sont particulièrement sujets aux coupures de connexion pour plusieurs raisons :</p>
            <ul>
                <li><strong>Changements d'adresse IP</strong> — en voyage ou lors d'un changement d'antenne, votre opérateur peut vous attribuer une nouvelle IP publique. Comme les connexions TCP sont liées à l'adresse IP, la session SSH existante devient immédiatement invalide.</li>
                <li><strong>Basculement Wi-Fi ↔ données mobiles</strong> — passer d'un réseau Wi-Fi aux données mobiles (ou inversement) change votre IP et rompt toute connexion TCP ouverte.</li>
                <li><strong>Délais d'inactivité</strong> — les opérateurs et routeurs NAT ferment souvent les connexions inactives après quelques minutes. Les sessions actives mais silencieuses (surveillance de logs, attente de saisie) sont vulnérables.</li>
                <li><strong>Perte de signal</strong> — tunnels, parkings souterrains ou simplement un signal faible peuvent brièvement interrompre le réseau, ce qui suffit à tuer une session.</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANT</div>
                Si vous exécutez une longue commande (une compilation, une sauvegarde, une migration de base de données) directement dans le terminal SSH et que la connexion se coupe, la commande est immédiatement interrompue. Tout travail partiel peut se retrouver dans un état incohérent.
            </div>
            <h3>La solution : tmux ou screen</h3>
            <p>Un multiplexeur de terminal exécute une session persistante <em>sur le serveur</em>, complètement indépendante de votre connexion SSH. Si la connexion se coupe, la session et tout ce qu'elle exécute continue. À la reconnexion, vous vous réattachez et trouvez tout exactement comme vous l'avez laissé.</p>
            <p>C'est l'habitude la plus utile pour quiconque gère des serveurs depuis un téléphone.</p>
            <h3>Démarrage rapide avec tmux</h3>
            <p><code>tmux</code> est disponible sur la plupart des distributions Linux modernes et est le choix recommandé.</p>
            <pre><code># Démarrer une nouvelle session nommée
tmux new -s work

# Se détacher de la session (la laisser tourner)
Ctrl+B, puis D

# Lister les sessions en cours
tmux ls

# Se rattacher à une session
tmux attach -t work

# Se rattacher à la session la plus récente
tmux attach</code></pre>
            <h3>Démarrage rapide avec screen</h3>
            <p><code>screen</code> est plus ancien mais disponible sur pratiquement tout système Unix, y compris les images serveur minimales où tmux peut ne pas être installé.</p>
            <pre><code># Démarrer une nouvelle session nommée
screen -S work

# Se détacher de la session
Ctrl+A, puis D

# Lister les sessions en cours
screen -ls

# Se rattacher à une session
screen -r work</code></pre>
            <h3>Flux de travail recommandé sur mobile</h3>
            <ol class="steps">
                <li>Connectez-vous au serveur avec SSHBorg.</li>
                <li>Démarrez ou rattachez-vous immédiatement à une session tmux/screen : <code>tmux attach || tmux new -s main</code></li>
                <li>Exécutez vos commandes dans le multiplexeur.</li>
                <li>Si la connexion se coupe, reconnectez-vous — votre session est toujours là.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// ASTUCE</div>
                Vous pouvez ajouter <code>tmux attach || tmux new -s main</code> à votre <code>~/.bashrc</code> ou <code>~/.zshrc</code> sur le serveur pour qu'une session multiplexeur démarre automatiquement à chaque connexion via SSHBorg.
            </div>`,

  doc_jump_hosts: `
            <h2>// JUMP HOSTS</h2>
            <p>Un jump host (aussi appelé hôte bastion) est un serveur intermédiaire par lequel vous devez passer pour atteindre un serveur cible non directement accessible depuis internet. SSHBorg prend en charge nativement les chaînes de sauts simples et multi-sauts.</p>
            <h3>Configurer un jump host dans SSHBorg</h3>
            <ol class="steps">
                <li>Ajoutez le serveur bastion comme hôte normal dans SSHBorg (ex. <em>bastion</em>).</li>
                <li>Ajoutez le serveur cible comme autre hôte.</li>
                <li>Dans les paramètres de l'hôte cible, définissez <strong>Jump host</strong> sur le bastion créé.</li>
                <li>Activez <strong>Transfert d'agent</strong> sur l'entrée du bastion — cela permet à votre clé d'être transférée via le bastion pour s'authentifier sur la cible.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// FONCTIONNEMENT</div>
                SSHBorg établit d'abord une connexion SSH au bastion, puis ouvre un canal TCP transféré via celui-ci vers le serveur cible. La clé privée ne quitte jamais le téléphone — le bastion ne fait que relayer le flux chiffré.
            </div>
            <h3>Chaînes multi-sauts</h3>
            <p>Si vous devez passer par plusieurs serveurs intermédiaires (ex. internet → bastion → dmz → cible), créez une entrée pour chaque saut et chaînez-les :</p>
            <ul>
                <li><strong>bastion</strong> — pas de jump host, transfert d'agent actif</li>
                <li><strong>dmz</strong> — jump host = bastion, transfert d'agent actif</li>
                <li><strong>target</strong> — jump host = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANT</div>
                Le transfert d'agent doit être activé sur <em>chaque saut intermédiaire</em>, pas seulement le premier. Sans cela, la chaîne d'authentification se rompt et la connexion au serveur final échoue avec une erreur « permission denied ».
            </div>
            <h3>Configuration manuelle équivalente (pour référence)</h3>
            <p>La configuration équivalente dans un <code>~/.ssh/config</code> sur ordinateur ressemble à ceci :</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>Avec cette configuration, <code>ssh target</code> sur votre ordinateur passe de façon transparente par le bastion.</p>
            <h3>Exigences du pare-feu</h3>
            <ul>
                <li>Votre téléphone doit pouvoir atteindre le bastion sur son port SSH (généralement 22).</li>
                <li>Le bastion doit pouvoir atteindre la cible sur son port SSH.</li>
                <li>La cible n'a <em>pas</em> besoin d'être accessible directement depuis votre téléphone.</li>
            </ul>`,

  doc_sessions: `
            <h2>// SESSIONS MULTIPLES</h2>
            <p>SSHBorg vous permet de garder plusieurs sessions de terminal SSH et de gestionnaire de fichiers SFTP ouvertes en même temps, même vers des serveurs différents.</p>
            <ul>
                <li>Ouvrez une session depuis l'écran des hôtes en appuyant sur <strong>Terminal</strong> ou <strong>SFTP</strong>.</li>
                <li>Basculez entre les sessions ouvertes via le sélecteur de sessions en haut de l'écran.</li>
                <li>Les sessions restent actives en arrière-plan tant que la connexion réseau tient.</li>
                <li>La liste des hôtes affiche un petit badge à côté de chaque hôte avec le nombre de sessions SSH et SFTP actives, pour voir d'un coup d'œil ce qui est ouvert.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// ASTUCE</div>
                Les commandes longues (compilations, sauvegardes, suivi de logs) continuent de s'exécuter même quand vous basculez vers une autre session. Utilisez un multiplexeur de terminal comme <code>tmux</code> ou <code>screen</code> côté serveur si vous souhaitez qu'elles survivent même à une coupure de connexion SSH.
            </div>`,

  doc_security: `
            <h2>// SÉCURITÉ DE L'APP</h2>
            <h3>Verrouillage biométrique</h3>
            <p>Activez le verrouillage biométrique dans <strong>Paramètres → Sécurité → Verrouillage biométrique</strong>. Quand il est actif, SSHBorg exige une empreinte digitale ou une reconnaissance faciale avant d'afficher des hôtes, des identifiants ou des données de session.</p>
            <p>Vous pouvez définir un délai d'inactivité — après ce nombre de minutes en arrière-plan, l'app se verrouille automatiquement.</p>
            <h3>Protection des captures d'écran</h3>
            <p>Par défaut, SSHBorg bloque les captures d'écran et l'enregistrement d'écran pour empêcher le contenu sensible du terminal de fuiter via l'écran des apps récentes ou des outils de capture.</p>
            <p>Si vous devez prendre une capture (ex. pour partager une sortie terminal), vous pouvez désactiver temporairement la protection dans <strong>Paramètres → Sécurité → Autoriser les captures</strong>.</p>
            <h3>Stockage des identifiants</h3>
            <p>Tous les identifiants (mots de passe, clés privées, phrases secrètes) sont stockés chiffrés en utilisant l'<strong>Android Keystore</strong> — une enclave sécurisée matérielle disponible sur Android 10+. Ils ne sont jamais écrits sur un stockage externe ni transmis nulle part.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTE SUR LES SAUVEGARDES</div>
                Comme les clés sont stockées dans l'Android Keystore, elles <strong>ne peuvent pas être sauvegardées</strong> via la sauvegarde cloud Android et ne seront pas transférées automatiquement vers un nouveau téléphone. Avant de changer d'appareil, assurez-vous d'autoriser une nouvelle clé générée sur le nouvel appareil sur tous vos serveurs.
            </div>`,
};

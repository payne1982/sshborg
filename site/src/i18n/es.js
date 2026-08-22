'use strict';
module.exports = {
  page_title:        'SSHBorg – Cliente SSH y SFTP para Android',
  meta_description:  'SSHBorg es un potente cliente SSH y SFTP para Android. Gestiona tus servidores de forma segura desde tu teléfono con autenticación por clave, almacenamiento cifrado y bloqueo biométrico.',

  nav_features:  'Funciones',
  nav_security:  'Seguridad',
  nav_guide:     'Guía de usuario',
  nav_support:   'Soporte',
  nav_privacy:   'Privacidad',
  nav_tip:       'Dejar propina',
  nav_contact:   'Contacto',

  hero_sub: 'Un potente cliente SSH &amp; SFTP para Android.<br>Gestiona tus servidores de forma segura, directamente desde tu teléfono.',

  badge_no_ads:      'Sin publicidad',
  badge_no_tracking: 'Sin seguimiento',
  badge_no_cloud:    'Sin nube',
  badge_free:        'Gratis',
  badge_android:     'Android 10+',

  cta_download:   'DESCARGAR',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',

  features_title: '// FUNCIONES',

  feat_terminal_title: 'TERMINAL SSH COMPLETO',
  feat_terminal_desc:  'Terminal interactivo con emulación VT100/xterm, soporte UTF-8 completo y múltiples sesiones simultáneas.',
  feat_sftp_title:     'GESTOR DE ARCHIVOS SFTP',
  feat_sftp_desc:      'Navega, sube, descarga, renombra y elimina archivos en tus servidores con un gestor de archivos intuitivo.',
  feat_keys_title:     'AUTENTICACIÓN POR CLAVE SSH',
  feat_keys_desc:      'Genera claves Ed25519, ECDSA y RSA directamente en tu dispositivo. Sin contraseñas.',
  feat_jump_title:     'SOPORTE PARA JUMP HOST',
  feat_jump_desc:      'Conéctate a través de uno o más bastiones con túnel transparente. Reenvío completo del agente SSH.',
  feat_biometric_title:'BLOQUEO DE LA APP',
  feat_biometric_desc: 'Bloquea la app con biometría, el PIN del dispositivo o un PIN o frase de contraseña en la app — funciona incluso en Android TV sin sensor de huellas. Tiempo de espera configurable.',
  feat_multilingual_title: 'MULTIIDIOMA',
  feat_multilingual_desc:  'Disponible en inglés, italiano, francés, alemán, español, portugués, ucraniano, ruso, chino y japonés.',
  feat_theme_title:    'TEMA OSCURO Y CLARO',
  feat_theme_desc:     'Sigue el tema del sistema o déjate elegir. Perfectamente legible en cualquier condición de iluminación.',
  feat_sessions_title: 'MÚLTIPLES SESIONES',
  feat_sessions_desc:  'Mantén varias sesiones SSH y SFTP abiertas simultáneamente. Cambia entre ellas al instante.',

  security_title: '// PRIVACIDAD BY DESIGN',
  security_desc:  'SSHBorg nunca recopila tus datos. Todo permanece en tu dispositivo — credenciales, claves, conexiones.',

  sec_badge_keystore:   'Cifrado Android Keystore',
  sec_badge_analytics:  'Sin analíticas',
  sec_badge_sdks:       'Sin SDKs de terceros',
  sec_badge_screenshots:'Protección de capturas',
  sec_badge_opensource: 'Código abierto — GPL v3',

  security_pp_link: 'Leer la política de privacidad completa &rarr;',

  tip_title:  '// DEJAR UNA PROPINA',
  tip_desc:   'SSHBorg es gratuito, sin publicidad y sin seguimiento. Si te ahorra tiempo, una pequeña propina ayuda a mantenerlo.',
  kofi_cta:        'Apóyame en Ko-fi',
  kofi_hero_cta:   'Déjame una propina',

  footer_privacy: 'Privacidad',
  footer_issues:  'Errores &amp; Comentarios',
  footer_source:  'Código fuente',
  footer_powered: 'Conectividad SSH proporcionada por',

  page_title_docs:       'SSHBorg – Guía de usuario',
  meta_description_docs: 'Guía operativa de SSHBorg: claves SSH, jump hosts, reenvío de agente, sugerencias de comandos, tmux y más.',

  nav_home:           'Inicio',
  nav_getting_started:'Primeros pasos',
  nav_ssh_keys:       'Claves SSH',
  nav_jump_hosts:     'Jump Hosts',
  nav_sftp:           'SFTP',
  nav_backup:         'Copia de seguridad',

  doc_page_title:    '// GUÍA DE USUARIO',
  doc_page_subtitle: 'Guía operativa — qué hacer, paso a paso, para sacar el máximo partido a SSHBorg.',

  toc_title: '// CONTENIDO',

  doc_toc: `            <li><a href="#adding-host">Añadir un host</a></li>
            <li><a href="#host-groups">Grupos de hosts</a></li>
            <li><a href="#sftp">Gestor de archivos SFTP</a></li>
            <li class="sub"><a href="#sftp">Navegación</a></li>
            <li class="sub"><a href="#sftp">Subir y descargar</a></li>
            <li class="sub"><a href="#sftp">Selección múltiple</a></li>
            <li><a href="#ssh-keys">Claves SSH</a></li>
            <li class="sub"><a href="#ssh-keys">Generar una clave</a></li>
            <li class="sub"><a href="#ssh-keys">Autorizar en el servidor</a></li>
            <li class="sub"><a href="#ssh-keys">Seguridad de las claves</a></li>
            <li><a href="#suggestions">Sugerencias de comandos</a></li>
            <li class="sub"><a href="#suggestions">Cómo funciona</a></li>
            <li class="sub"><a href="#suggestions">Solución de problemas</a></li>
            <li><a href="#terminal">Gestos del terminal</a></li>
            <li><a href="#terminal-settings">Ajustes del terminal</a></li>
            <li><a href="#extra-keys">Barra de teclas extra</a></li>
            <li><a href="#agent-forwarding">Reenvío de agente</a></li>
            <li><a href="#legacy-ciphers">Cifrados heredados</a></li>
            <li><a href="#connection-drops">Caídas de conexión</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">Jump Hosts</a></li>
            <li class="sub"><a href="#jump-hosts">Cadenas multi-salto</a></li>
            <li><a href="#sessions">Sesiones múltiples</a></li>
            <li><a href="#security">Seguridad de la app</a></li>
            <li><a href="#backup">Copia de seguridad</a></li>
            <li><a href="#android-tv">Android TV</a></li>`,

  doc_adding_host: `
            <h2>// AÑADIR UN HOST</h2>
            <p>Pulsa el botón <strong>+</strong> en la pantalla de hosts para añadir un nuevo servidor.</p>
            <h3>Campos obligatorios</h3>
            <ul>
                <li><strong>Hostname / IP</strong> — la dirección o IP del servidor. Se admiten tanto IPv4 como IPv6.</li>
                <li><strong>Puerto</strong> — valor predeterminado: 22. Cámbialo si tu servidor usa un puerto SSH diferente.</li>
                <li><strong>Nombre de usuario</strong> — el usuario Unix con el que quieres iniciar sesión (p. ej. <code>ubuntu</code>, <code>root</code>, <code>deploy</code>).</li>
                <li><strong>Autenticación</strong> — elige entre contraseña o clave SSH (recomendado).</li>
            </ul>
            <h3>Verificación de la huella del host</h3>
            <p>En la primera conexión, SSHBorg muestra la huella del servidor y te pide que la aceptes. Es una comprobación de seguridad: garantiza que te estás conectando a la máquina correcta y no a un impostor. Verifica que la huella coincida con la que te proporcionó el administrador del servidor antes de aceptarla.</p>
            <p>Una vez aceptada, la huella se almacena localmente. Si cambia en una conexión futura, SSHBorg te avisará — podría indicar una reinstalación del servidor, una rotación de claves o un ataque man-in-the-middle.</p>
            <div class="callout callout-info">
                <div class="callout-label">// CONSEJO</div>
                Puedes comprobar la huella del servidor en cualquier momento con:
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_host_groups: `
            <h2>// GRUPOS DE HOSTS</h2>
            <p>Cuando la lista de servidores crece, puedes organizar los hosts en grupos plegables con códigos de color — por ejemplo <em>Producción</em>, <em>Laboratorio</em>, <em>Clientes</em>.</p>
            <h3>Crear un grupo</h3>
            <ol class="steps">
                <li>Añade o edita un host y abre el desplegable <strong>Grupo</strong>.</li>
                <li>Elige <strong>Nuevo grupo…</strong>, escribe un nombre y selecciona uno de los colores predefinidos.</li>
                <li>Guarda el host — la lista de hosts mostrará el grupo como una sección propia.</li>
            </ol>
            <h3>Trabajar con grupos</h3>
            <ul>
                <li><strong>Plegar / desplegar</strong> — toca la cabecera de un grupo para plegarlo o volver a abrirlo. El estado se recuerda, incluso tras reiniciar la app.</li>
                <li><strong>Color</strong> — el color del grupo aparece como un punto en la cabecera y como tinte de los iconos de los hosts del grupo.</li>
                <li><strong>Editar</strong> — mantén pulsada la cabecera y elige <em>Editar</em> para renombrar el grupo o cambiar su color.</li>
                <li><strong>Eliminar</strong> — mantén pulsada la cabecera y elige <em>Eliminar</em>. Los hosts del grupo <em>no</em> se borran: simplemente quedan sin grupo.</li>
            </ul>
            <p>Los colores se eligen de las muestras rápidas o libremente con el selector de degradado. Un host también puede tener un <strong>color propio</strong> — se define en el editor del host, justo debajo del grupo — que prevalece sobre el color del grupo y funciona también para hosts sin grupo.</p>
            <p>Los hosts sin grupo permanecen al principio de la lista y, si no creas ningún grupo, la lista se ve y se comporta exactamente como antes.</p>`,

  doc_sftp: `
            <h2>// GESTOR DE ARCHIVOS SFTP</h2>
            <p>El gestor de archivos SFTP te permite explorar, subir, descargar, renombrar y eliminar archivos en tu servidor directamente desde el teléfono. Abre una sesión SFTP desde la pantalla de hosts tocando <strong>SFTP</strong>.</p>
            <h3>Navegación</h3>
            <p>Toca una carpeta para abrirla. Usa la flecha de retroceso o toca cualquier segmento de la barra de ruta para subir en el árbol de directorios.</p>
            <p>Los enlaces simbólicos se muestran con un pequeño distintivo de cadena. Al tocar un enlace simbólico se navega a su destino: si apunta a un directorio se entra en él; si apunta a un archivo se comporta como un archivo normal.</p>
            <h3>Subir archivos</h3>
            <p>Toca el botón de <strong>subida</strong> (↑) para seleccionar uno o varios archivos del almacenamiento del teléfono. La subida comienza de inmediato y el progreso se muestra en la parte superior de la pantalla.</p>
            <h3>Descargar archivos y carpetas</h3>
            <p>Toca cualquier archivo para descargarlo inmediatamente. Para descargar una carpeta completa, toca el icono de <strong>descarga</strong> que aparece junto a ella — SSHBorg descargará todo el árbol de directorios y lo guardará en la carpeta <strong>Descargas</strong> del teléfono.</p>
            <p>Si un archivo ya existe en el destino, un diálogo te preguntará si deseas <strong>sobrescribir</strong>, <strong>omitir</strong> el archivo o <strong>cancelar</strong> toda la transferencia.</p>
            <div class="callout callout-info">
                <div class="callout-label">// NOTA SOBRE ENLACES SIMBÓLICOS</div>
                Durante la descarga de una carpeta, los enlaces simbólicos que apuntan a directorios se omiten — solo se descargan los archivos normales (incluidos los enlaces a archivos). Esto evita descargas recursivas no deseadas.
            </div>
            <h3>Selección múltiple y operaciones por lotes</h3>
            <p>Mantén pulsado cualquier elemento para entrar en el modo de selección y toca otros elementos para ampliar la selección. La barra de herramientas muestra las acciones disponibles:</p>
            <ul>
                <li><strong>Descargar</strong> — descarga todos los archivos y carpetas seleccionados de una vez, con un diálogo de progreso y opción de cancelación.</li>
                <li><strong>Eliminar</strong> — elimina todos los elementos seleccionados. Eliminar una carpeta no vacía borra todo su contenido de forma recursiva. <em>Esta acción no se puede deshacer.</em></li>
            </ul>`,

  doc_ssh_keys: `
            <h2>// CLAVES SSH</h2>
            <p>La autenticación por clave es más segura que las contraseñas y, una vez configurada, no requiere recordar ni escribir nada.</p>
            <h3>Generar una clave</h3>
            <p>Ve a <strong>Ajustes → Claves SSH → Generar nueva clave</strong>. SSHBorg admite:</p>
            <ul>
                <li><strong>Ed25519</strong> — recomendado. Rápido, compacto y seguro.</li>
                <li><strong>ECDSA (P-256 / P-384)</strong> — buena compatibilidad con servidores más antiguos.</li>
                <li><strong>RSA (2048 / 4096 bits)</strong> — máxima compatibilidad, pero más lento.</li>
            </ul>
            <p>Ponle un nombre significativo a la clave (p. ej. <em>mi-vps</em> o <em>servidor-trabajo</em>) para identificarla más adelante.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DE SEGURIDAD</div>
                SSHBorg no permite intencionalmente exportar claves privadas. La clave nunca sale del dispositivo. Si necesitas la misma clave en otro dispositivo, genera una nueva allí y autorízala por separado en tus servidores — es el enfoque más seguro.
            </div>
            <h3>Autorizar la clave en el servidor</h3>
            <p>Tras generar una clave, pulsa sobre ella para ver la <strong>clave pública</strong>. Cópiala y pégala en el archivo <code>~/.ssh/authorized_keys</code> del servidor para el usuario con el que quieres iniciar sesión.</p>
            <ol class="steps">
                <li>En tu teléfono, abre SSHBorg → Ajustes → Claves SSH → pulsa la clave → copia la clave pública.</li>
                <li>Inicia sesión en tu servidor (con contraseña o con otra clave que ya tengas).</li>
                <li>Añade la clave pública al archivo de claves autorizadas:
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...tuclavecopiada..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>Intenta conectarte con SSHBorg — debería iniciar sesión sin pedir contraseña.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// REQUISITO DEL SERVIDOR</div>
                Asegúrate de que tu servidor tenga <code>PubkeyAuthentication yes</code> en <code>/etc/ssh/sshd_config</code>. Es el valor predeterminado en la mayoría de distribuciones, pero algunas imágenes reforzadas lo deshabilitan.
            </div>
            <h3>Cifrado adicional de la clave</h3>
            <p>SSHBorg ofrece una <strong>frase de contraseña adicional</strong> opcional para tus claves (Ajustes → Claves SSH → pulsa una clave → Activar cifrado). Cuando está activa, la clave se cifra con una frase que SSHBorg no almacena — se te pedirá cada vez que se use la clave.</p>
            <p>Es muy recomendable si almacenas credenciales sensibles en tu teléfono o no tienes activado un bloqueo de la app.</p>`,

  doc_suggestions: `
            <h2>// SUGERENCIAS DE COMANDOS</h2>
            <p>SSHBorg muestra una barra de sugerencias sobre el teclado mientras escribes en el terminal. Las sugerencias provienen del historial de shell del usuario con el que te conectaste.</p>
            <h3>Cómo funciona</h3>
            <p>Al abrir una sesión de terminal, SSHBorg lee el archivo de historial de shell del servidor remoto. Comprueba las siguientes ubicaciones en orden:</p>
            <ol>
                <li><code>~/.bash_history</code> — predeterminado para shells Bash</li>
                <li><code>~/.zsh_history</code> — predeterminado para Zsh (también comprobado como <code>$HISTFILE</code> si está configurado)</li>
                <li><code>~/.local/share/fish/fish_history</code> — para usuarios de Fish shell</li>
            </ol>
            <p>Se usa el primer archivo que existe y es legible. Mientras escribes, los comandos se filtran en tiempo real y se muestran como chips en la barra de sugerencias. Pulsa un chip para insertar el comando.</p>
            <h3>Solución de problemas</h3>
            <p><strong>No aparecen sugerencias</strong></p>
            <ul>
                <li>El archivo de historial puede que aún no exista (primer inicio de sesión o shell no configurada para guardar el historial).</li>
                <li>Asegúrate de que tu shell esté configurada para guardar el historial. Para Bash, añade a <code>~/.bashrc</code>:
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>Para Zsh, añade a <code>~/.zshrc</code>:
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>Las sugerencias pertenecen al usuario equivocado</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// LIMITACIÓN CONOCIDA</div>
                Si te conectas como un usuario y luego ejecutas <code>sudo su - root</code> (o cambias a otro usuario con <code>su</code>), la barra de sugerencias sigue mostrando el historial del <em>usuario de inicio de sesión original</em>, no de <code>root</code>. Esto es porque SSHBorg lee el archivo de historial antes de que arranque el shell, usando las credenciales con las que te conectaste.
                <br><br>
                Para obtener sugerencias del historial de root, añade una entrada de host separada en SSHBorg configurada para iniciar sesión directamente como <code>root</code> (si tu servidor lo permite).
            </div>`,

  doc_terminal: `
            <h2>// GESTOS DEL TERMINAL</h2>
            <p>El terminal responde a algunos gestos táctiles además de la escritura:</p>
            <ul>
                <li><strong>Desplazar el historial</strong> — desliza hacia arriba o abajo para navegar por el buffer de desplazamiento del terminal.</li>
                <li><strong>Zoom</strong> — pellizca para aumentar o reducir el tamaño del texto.</li>
                <li><strong>Copiar texto</strong> — mantén pulsado en cualquier lugar del terminal para entrar en modo selección. Arrastra los controladores para ajustar el área seleccionada, luego toca <em>Copiar selección</em> para copiar solo el texto resaltado, o <em>Copiar todo</em> para copiar toda la salida. Toca fuera para cancelar.</li>
                <li><strong>Pegar</strong> — usa el botón <em>Pegar</em> en la barra de teclas adicionales (visible cuando el teclado está abierto).</li>
            </ul>`,

  doc_terminal_settings: `
            <h2>// AJUSTES DEL TERMINAL</h2>
            <p>En <strong>Ajustes → Terminal</strong> puedes adaptar el terminal a tus necesidades:</p>
            <ul>
                <li><strong>Colores del terminal</strong> — el clásico esquema <em>oscuro</em> (blanco sobre negro), un esquema <em>claro</em> (negro sobre blanco) más legible a plena luz, o <em>seguir el tema de la app</em>, que cambia automáticamente junto con el tema de la app. El cambio se aplica al instante, incluso a las sesiones ya abiertas.</li>
                <li><strong>Mantener la pantalla encendida</strong> — evita que la pantalla se apague mientras un terminal está abierto. Útil al vigilar logs o comandos de larga duración. Desactivado por defecto.</li>
                <li><strong>Tamaño de letra predeterminado</strong> — el tamaño del texto con el que arrancan las nuevas sesiones; siempre puedes hacer zoom con dos dedos en cada sesión.</li>
                <li><strong>Scrollback</strong>, <strong>desplazamiento invertido</strong> y <strong>sugerencias de comandos</strong> — controlan cuánto historial de salida se conserva, la dirección del desplazamiento y la barra de sugerencias descrita más arriba.</li>
                <li><strong>Acción de doble toque</strong> — si quieres, un doble toque en el terminal envía <em>Tab</em> (autocompletado) o <em>Tab</em> dos veces (lista todos los candidatos). Desactivado de forma predeterminada.</li>
            </ul>`,

  doc_extra_keys: `
            <h2>// BARRA DE TECLAS EXTRA</h2>
            <p>Cuando el teclado virtual está abierto, aparece sobre él una fila de botones de acceso rápido. Desplaza la barra lateralmente para acceder a todas las teclas.</p>
            <h3>Teclas modificadoras</h3>
            <p><strong>Ctrl</strong> y <strong>Alt</strong> son conmutadores persistentes — toca uno y luego toca una tecla de letra para enviar la combinación. Se resetean automáticamente tras la siguiente pulsación.</p>
            <ul>
                <li><strong>Ctrl+C</strong> — interrumpe el proceso en ejecución.</li>
                <li><strong>Ctrl+D</strong> — envía EOF / cierra la shell.</li>
                <li><strong>Ctrl+Z</strong> — suspende el proceso.</li>
                <li><strong>Ctrl+L</strong> — limpia la pantalla.</li>
            </ul>
            <h3>Modo palabra</h3>
            <p>El icono de corrección ortográfica alterna el teclado entre <em>modo terminal</em> y <em>modo palabra</em>. En modo terminal (predeterminado) la autocorrección y las sugerencias de palabras están desactivadas — ideal para comandos y rutas de archivo. En modo palabra el teclado funciona como un campo de texto normal, con sugerencias y autocorrección activadas. Útil cuando se escribe lenguaje natural por SSH, por ejemplo con Claude Code u otras herramientas interactivas.</p>
            <h3>Navegación y edición</h3>
            <ul>
                <li><strong>ESC</strong> — tecla Escape.</li>
                <li><strong>Tab</strong> — autocompletado de la shell.</li>
                <li><strong>↑ ↓ ← →</strong> — teclas de flecha del cursor.</li>
                <li><strong>Home / End</strong> — salta al inicio o al final de la línea.</li>
                <li><strong>PgUp / PgDn</strong> — página arriba / página abajo.</li>
                <li><strong>Del</strong> — eliminar hacia adelante (carácter a la derecha del cursor).</li>
                <li><strong>Pegar</strong> — pega el portapapeles en el terminal.</li>
            </ul>
            <h3>Teclas de función</h3>
            <p>Desplaza la barra hacia la derecha para acceder a las teclas <strong>F1 a F12</strong>.</p>`,

  doc_agent_forwarding: `
            <h2>// REENVÍO DE AGENTE</h2>
            <p>El reenvío del agente SSH permite usar las claves almacenadas en SSHBorg para autenticar conexiones adicionales realizadas <em>desde dentro</em> del servidor remoto — por ejemplo, para <code>git clone</code> de un repo privado, o para saltar a una segunda máquina.</p>
            <h3>Activar el reenvío en SSHBorg</h3>
            <p>Al añadir o editar un host, activa el interruptor <strong>Reenvío de agente</strong>. SSHBorg actuará como agente SSH para esa sesión.</p>
            <h3>Configuración en el servidor</h3>
            <p>El servidor debe permitir el reenvío de agente. Comprueba <code>/etc/ssh/sshd_config</code>:</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>Este es el valor predeterminado en la mayoría de sistemas. Después de modificarlo, reinicia el demonio SSH:</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>Configuración de cliente por host (opcional)</h3>
            <p>Si también te conectas a este servidor desde un portátil o escritorio, puedes configurar el reenvío de forma permanente en tu <code>~/.ssh/config</code> local:</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DE SEGURIDAD</div>
                El reenvío de agente da al servidor remoto acceso temporal al socket de tu agente SSH. Un usuario root (o un proceso comprometido) en ese servidor podría usar tus claves para conectarse a otros sitios mientras tu sesión está activa. Activa el reenvío solo en servidores de confianza.
            </div>`,

  doc_legacy_ciphers: `
            <h2>// CIFRADOS HEREDADOS</h2>
            <p>Algunos servidores más antiguos — equipos de red, dispositivos embebidos o sistemas con versiones obsoletas de OpenSSH — solo soportan algoritmos de cifrado que los clientes SSH modernos ya no anuncian por defecto.</p>
            <p>Al activar el interruptor <strong>Permitir cifrados heredados</strong> en la configuración del host, SSHBorg añade los siguientes algoritmos a la lista de negociación:</p>
            <h3>Algoritmos añadidos</h3>
            <ul>
                <li><strong>Cifrados:</strong> <code>aes128-cbc</code>, <code>aes192-cbc</code>, <code>aes256-cbc</code>, <code>3des-cbc</code></li>
                <li><strong>Intercambio de claves:</strong> <code>diffie-hellman-group14-sha1</code>, <code>diffie-hellman-group-exchange-sha1</code>, <code>diffie-hellman-group1-sha1</code></li>
                <li><strong>Tipo de clave de host:</strong> <code>ssh-dss</code> (DSA 1024-bit)</li>
            </ul>
            <p>El servidor siempre negocia el algoritmo más fuerte soportado por ambas partes, por lo que activar esta opción no debilita las conexiones a servidores modernos.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA DE SEGURIDAD</div>
                Los algoritmos de esta lista se consideran criptográficamente débiles. Active esta opción solo para servidores que no pueda actualizar.
            </div>`,

  doc_connection_drops: `
            <h2>// CAÍDAS DE CONEXIÓN Y MULTIPLEXORES DE TERMINAL</h2>
            <p>SSH es una conexión TCP activa entre tu teléfono y el servidor. Si la conexión se interrumpe — aunque sea por un segundo — la sesión y todo lo que se ejecutaba en ella se pierde.</p>
            <h3>Por qué caen las conexiones en móvil</h3>
            <p>Las redes móviles son especialmente propensas a las caídas de conexión por varias razones:</p>
            <ul>
                <li><strong>Cambios de dirección IP</strong> — al viajar o cambiar de antena, tu operador puede asignarte una nueva IP pública. Como las conexiones TCP están vinculadas a la dirección IP, la sesión SSH existente se invalida inmediatamente.</li>
                <li><strong>Cambio Wi-Fi ↔ datos móviles</strong> — cambiar entre una red Wi-Fi y datos móviles (o viceversa) cambia tu IP y rompe cualquier conexión TCP abierta.</li>
                <li><strong>Tiempos de espera por inactividad</strong> — los operadores y routers NAT suelen cerrar las conexiones inactivas tras unos minutos. Las sesiones activas pero silenciosas (observar logs, esperar entrada) son vulnerables.</li>
                <li><strong>Pérdida de señal</strong> — túneles, aparcamientos subterráneos o simplemente una señal débil pueden interrumpir brevemente la red, lo que es suficiente para matar una sesión.</li>
                <li><strong>Límites de Android en segundo plano</strong> — para ahorrar batería, Android limita cuánto tiempo puede seguir trabajando una app en segundo plano. Tras unas seis horas acumuladas en segundo plano, el sistema detiene las sesiones de SSHBorg; recibes una notificación y puedes reconectarte volviendo a abrir la app. Llevar la app a primer plano restablece este límite.</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                Si estás ejecutando un comando largo (una compilación, una copia de seguridad, una migración de base de datos) directamente en el terminal SSH y la conexión cae, el comando se interrumpe inmediatamente. Cualquier trabajo parcial puede quedar en un estado inconsistente.
            </div>
            <h3>La solución: tmux o screen</h3>
            <p>Un multiplexor de terminal ejecuta una sesión persistente <em>en el servidor</em>, completamente independiente de tu conexión SSH. Si la conexión cae, la sesión y todo lo que ejecuta sigue funcionando. Al reconectarte, te reincorporas y encuentras todo exactamente como lo dejaste.</p>
            <p>Es el hábito más útil para quien gestiona servidores desde el teléfono.</p>
            <h3>Inicio rápido con tmux</h3>
            <p><code>tmux</code> está disponible en la mayoría de distribuciones Linux modernas y es la opción recomendada.</p>
            <pre><code># Iniciar una nueva sesión con nombre
tmux new -s work

# Desconectarse de la sesión (dejarla en ejecución)
Ctrl+B, luego D

# Listar sesiones en ejecución
tmux ls

# Reconectarse a una sesión
tmux attach -t work

# Reconectarse a la sesión más reciente
tmux attach</code></pre>
            <h3>Inicio rápido con screen</h3>
            <p><code>screen</code> es más antiguo pero disponible en prácticamente cualquier sistema Unix, incluyendo imágenes mínimas donde tmux puede no estar instalado.</p>
            <pre><code># Iniciar una nueva sesión con nombre
screen -S work

# Desconectarse de la sesión
Ctrl+A, luego D

# Listar sesiones en ejecución
screen -ls

# Reconectarse a una sesión
screen -r work</code></pre>
            <h3>Flujo de trabajo recomendado en móvil</h3>
            <ol class="steps">
                <li>Conéctate al servidor con SSHBorg.</li>
                <li>Inicia o reconéctate inmediatamente a una sesión tmux/screen: <code>tmux attach || tmux new -s main</code></li>
                <li>Ejecuta tus comandos dentro del multiplexor.</li>
                <li>Si la conexión cae, simplemente vuelve a conectarte — tu sesión sigue ahí.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// CONSEJO</div>
                Puedes añadir <code>tmux attach || tmux new -s main</code> a tu <code>~/.bashrc</code> o <code>~/.zshrc</code> en el servidor para que una sesión de multiplexor se inicie automáticamente cada vez que inicies sesión a través de SSHBorg.
            </div>`,

  doc_jump_hosts: `
            <h2>// JUMP HOSTS</h2>
            <p>Un jump host (también llamado bastion host) es un servidor intermedio a través del cual debes pasar para llegar a un servidor de destino no accesible directamente desde internet. SSHBorg admite cadenas de salto simples y multi-salto de forma nativa.</p>
            <h3>Configurar un jump host en SSHBorg</h3>
            <ol class="steps">
                <li>Añade el servidor bastión como host normal en SSHBorg (p. ej. <em>bastion</em>).</li>
                <li>Añade el servidor de destino como otro host.</li>
                <li>En los ajustes del host de destino, establece <strong>Jump host</strong> en el bastión creado.</li>
                <li>Activa <strong>Reenvío de agente</strong> en la entrada del bastión — esto permite que tu clave se reenvíe a través del bastión para autenticarse en el destino.</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// CÓMO FUNCIONA</div>
                SSHBorg establece primero una conexión SSH al bastión y luego abre un canal TCP reenviado a través de él hacia el servidor de destino. La clave privada nunca sale del teléfono — el bastión solo actúa como proxy del flujo cifrado.
            </div>
            <h3>Cadenas multi-salto</h3>
            <p>Si necesitas saltar a través de más de un servidor intermedio (p. ej. internet → bastión → dmz → destino), crea una entrada para cada salto y encadénalas:</p>
            <ul>
                <li><strong>bastion</strong> — sin jump host, reenvío de agente activo</li>
                <li><strong>dmz</strong> — jump host = bastion, reenvío de agente activo</li>
                <li><strong>target</strong> — jump host = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                El reenvío de agente debe estar activado en <em>cada salto intermedio</em>, no solo en el primero. Sin él, la cadena de autenticación se rompe y la conexión al servidor final fallará con un error "permission denied".
            </div>
            <h3>Configuración manual equivalente (como referencia)</h3>
            <p>La configuración equivalente en un <code>~/.ssh/config</code> de escritorio tiene este aspecto:</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>Con esta configuración, <code>ssh target</code> en tu portátil salta de forma transparente a través del bastión.</p>
            <h3>Requisitos del cortafuegos</h3>
            <ul>
                <li>Tu teléfono debe poder llegar al bastión en su puerto SSH (normalmente el 22).</li>
                <li>El bastión debe poder llegar al destino en su puerto SSH.</li>
                <li>El destino <em>no</em> necesita ser accesible directamente desde tu teléfono.</li>
            </ul>`,

  doc_sessions: `
            <h2>// SESIONES MÚLTIPLES</h2>
            <p>SSHBorg te permite mantener abiertas varias sesiones de terminal SSH y gestor de archivos SFTP al mismo tiempo, incluso hacia servidores diferentes.</p>
            <ul>
                <li>Abre una sesión desde la pantalla de hosts pulsando <strong>Terminal</strong> o <strong>SFTP</strong>.</li>
                <li>Cambia entre sesiones abiertas usando el selector de sesiones en la parte superior de la pantalla.</li>
                <li>Las sesiones permanecen activas en segundo plano mientras se mantenga la conexión de red.</li>
                <li>La lista de hosts muestra un pequeño indicador junto a cada host con el número de sesiones SSH y SFTP activas, para ver de un vistazo qué está abierto.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// CONSEJO</div>
                Los comandos de larga duración (compilaciones, copias de seguridad, seguimiento de logs) siguen ejecutándose aunque cambies a otra sesión. Usa un multiplexor de terminal como <code>tmux</code> o <code>screen</code> en el servidor si quieres que sobrevivan incluso a una caída de la conexión SSH.
            </div>`,

  doc_security: `
            <h2>// SEGURIDAD DE LA APP</h2>
            <h3>Bloqueo de la app</h3>
            <p>Elige cómo se protege la app en <strong>Ajustes → Seguridad → Bloqueo de la app</strong>: <strong>Ninguno</strong> (predeterminado), <strong>Solo biométrico</strong> (huella dactilar o reconocimiento facial), <strong>Bloqueo del dispositivo</strong> (el PIN, el patrón o la contraseña del dispositivo, además de la biometría) o un <strong>PIN o frase de contraseña</strong> en la app. Cuando hay un bloqueo activo, SSHBorg requiere autenticación antes de mostrar cualquier host, credencial o dato de sesión.</p>
            <p>El <strong>PIN o frase de contraseña</strong> en la app funciona en cualquier dispositivo, incluso sin hardware biométrico ni un bloqueo de pantalla del sistema, por lo que es la opción adecuada en una Android TV. Para cambiarlo o quitarlo se pide primero el actual. No se puede recuperar si se olvida: tendrías que borrar los datos de la app o reinstalarla, así que guarda una copia de seguridad de tus hosts.</p>
            <p>Puedes establecer un tiempo de espera por inactividad — tras ese número de minutos en segundo plano, la app se bloquea automáticamente.</p>
            <h3>Protección de capturas de pantalla</h3>
            <p>Por defecto, SSHBorg bloquea las capturas de pantalla y la grabación de pantalla para evitar que el contenido sensible del terminal se filtre a través de la pantalla de apps recientes o herramientas de captura.</p>
            <p>Si necesitas hacer una captura (p. ej. para compartir una salida del terminal), puedes desactivar temporalmente la protección en <strong>Ajustes → Seguridad → Permitir capturas</strong>.</p>
            <h3>Almacenamiento de credenciales</h3>
            <p>Todas las credenciales (contraseñas, claves privadas, frases de contraseña) se almacenan cifradas usando el <strong>Android Keystore</strong> — un enclave seguro respaldado por hardware disponible en Android 10+. Nunca se escriben en almacenamiento externo ni se transmiten a ningún lugar.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// NOTA SOBRE COPIAS DE SEGURIDAD</div>
                Como las claves se almacenan en el Android Keystore, <strong>no pueden incluirse en la copia de seguridad</strong> en la nube de Android y no se transferirán automáticamente a un nuevo teléfono. Antes de cambiar de dispositivo, asegúrate de autorizar una nueva clave generada en el nuevo dispositivo en todos tus servidores.
            </div>`,

  doc_android_tv: `
            <h2>// ANDROID TV</h2>
            <p>SSHBorg funciona en Android TV y Google TV. Se maneja con el D-pad del mando, pero al ser una app con mucho texto, un teclado físico marca una gran diferencia.</p>
            <h3>Navegar con el mando</h3>
            <ul>
                <li><strong>Flechas del D-pad</strong> — mueven el foco entre filas y controles.</li>
                <li><strong>OK (centro)</strong> — la acción principal: conectar a un host, abrir una carpeta, descargar un archivo, expandir un grupo.</li>
                <li><strong>Atrás</strong> — subir un nivel o salir de la pantalla.</li>
            </ul>
            <h3>Abrir el menú de una fila</h3>
            <p>Para renombrar, eliminar, editar, duplicar o descargar desde una fila, abre su menú: pulsa la <strong>tecla Menú (opciones)</strong> del mando sobre la fila enfocada, o <strong>mantén pulsado OK</strong> (pulsación larga). OK por sí solo ejecuta la acción principal, no el menú.</p>
            <h3>Escribir: usa un teclado físico</h3>
            <p>Se recomienda encarecidamente un <strong>teclado físico — USB o Bluetooth</strong> (Android TV admite ambos), y es prácticamente imprescindible para el terminal. Con él, los campos de texto y la shell funcionan con normalidad.</p>
            <p>Sin él, el teclado en pantalla sirve para entradas cortas: enfoca un campo, pulsa OK para abrirlo, escribe y pulsa <strong>OK / Ir</strong> en el teclado para confirmar — en la solicitud de contraseña esto conecta directamente. Escribir comandos de shell con el teclado en pantalla, sin embargo, es poco práctico.</p>
            <h3>Bloquear la app en un TV</h3>
            <p>Un TV normalmente no tiene lector de huellas ni bloqueo de pantalla, así que protege la app con el bloqueo integrado por <strong>PIN o frase de contraseña</strong> (Ajustes → Seguridad) — funciona por completo con el mando o un teclado. Consulta <a href="#security">Seguridad de la app</a>.</p>`,

  doc_backup: `
            <h2>// COPIA DE SEGURIDAD DE LA CONFIGURACIÓN</h2>
            <p>SSHBorg puede exportar e importar configuraciones de hosts y ajustes de la app como archivo JSON. Esto permite transferir la lista de servidores a otro dispositivo o conservar una copia de seguridad portátil de tu configuración.</p>
            <div class="callout callout-warn">
                <div class="callout-label">// IMPORTANTE</div>
                La copia de seguridad incluye las configuraciones de los hosts y los ajustes de la app (terminal, apariencia, comportamiento). <strong>Las contraseñas y las claves SSH nunca se exportan</strong> — el material de la clave debe configurarse de nuevo en un dispositivo nuevo. No obstante, la copia registra el <em>nombre</em> de la clave que usa cada host: si vuelves a crear una clave con el mismo nombre antes de importar, sus hosts se vuelven a vincular automáticamente.
            </div>
            <h3>Exportar</h3>
            <p>Ve a <strong>Ajustes → Copia de seguridad → Exportar copia</strong>. Elige dónde guardar el archivo usando el selector de archivos del sistema. El archivo se llama <code>sshborg_backup.json</code> por defecto.</p>
            <h3>Importar</h3>
            <p>Ve a <strong>Ajustes → Copia de seguridad → Importar copia</strong>. Selecciona el archivo <code>.json</code> exportado previamente (o creado manualmente). SSHBorg lo fusionará con la lista de hosts existente:</p>
            <ul>
                <li>Los hosts cuyo <strong>nombre</strong> coincide con una entrada existente se <strong>actualizan</strong>.</li>
                <li>Los hosts con un nombre nuevo se <strong>añaden</strong>.</li>
                <li>Los hosts no presentes en el archivo permanecen <strong>sin cambios</strong>.</li>
                <li>Un host actualizado conserva su contraseña, clave y clave de host aceptada — la copia nunca las contiene.</li>
                <li>Un host sin clave se vincula a una clave cuyo nombre coincide con el <code>keyLabel</code> exportado, si existe; de lo contrario, el campo se ignora.</li>
</ul>
            <h3>Formato JSON</h3>
            <p>El archivo exportado es un objeto JSON estándar. También puedes crearlo manualmente para importar en bloque una lista de servidores desde otra fuente.</p>
            <pre><code>{
  "version": 5,
  "exported_at": "2026-05-14T10:00:00Z",
  "groups": [
    { "name": "Producción", "color": -1754827 }
  ],
  "hosts": [
    {
      "label":           "Mi VPS",
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
      "keyLabel":         "Clave de mi VPS",
      "group":           "Producción"
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
            <p>El objeto <code>settings</code> (opcional) contiene los ajustes de la app y se escribe automáticamente al exportar; puedes omitirlo si creas el archivo a mano.</p>
            <h3>Referencia de campos</h3>
            <ul>
                <li><code>label</code> — nombre mostrado en SSHBorg. Se usa como clave única para la fusión al importar. <strong>Obligatorio.</strong></li>
                <li><code>hostname</code> — dirección o IP del servidor (IPv4 o IPv6). <strong>Obligatorio.</strong></li>
                <li><code>port</code> — puerto SSH. Por defecto: <code>22</code>.</li>
                <li><code>username</code> — usuario de inicio de sesión. <strong>Obligatorio.</strong></li>
                <li><code>agentForwarding</code> — <code>true</code> para habilitar el reenvío del agente SSH. Por defecto: <code>false</code>.</li>
                <li><code>jumpMode</code> — <code>"simple"</code> (usa el texto de <code>jumpHosts</code>) o <code>"host_list"</code> (usa IDs internos de SSHBorg). Usa <code>"simple"</code> al crear el archivo manualmente.</li>
                <li><code>jumpHosts</code> — jump hosts separados por comas en formato <code>[usuario@]host[:puerto]</code>. Solo se usa cuando <code>jumpMode</code> es <code>"simple"</code>.</li>
                <li><code>portForwardings</code> — reglas de reenvío de puerto local separadas por saltos de línea en sintaxis SSH <code>-L</code>, p. ej. <code>"8080:localhost:8080"</code>.</li>
                <li><code>sftpStartMode</code> — directorio de inicio SFTP: <code>"last"</code> (recordar el último visitado), <code>"fixed"</code> (usar siempre <code>sftpStartDir</code>), <code>"home"</code> (directorio principal del servidor). Por defecto: <code>"last"</code>.</li>
                <li><code>sftpStartDir</code> — ruta a usar cuando <code>sftpStartMode</code> es <code>"fixed"</code>.</li>
                <li><code>sftpShowHidden</code> — <code>true</code> para mostrar los archivos ocultos (dotfiles, nombres que empiezan por ".") en el explorador SFTP de este host. Predeterminado <code>false</code>.</li>
                <li><code>allowLegacyCiphers</code> — <code>true</code> para habilitar los algoritmos heredados descritos más arriba. Por defecto <code>false</code>.</li>
                <li><code>keyLabel</code> — nombre de la clave SSH que usa este host. Al importar, si existe una clave con este nombre se vincula al host; de lo contrario, el campo se ignora. La clave en sí nunca se incluye en la copia.</li>
                <li><code>group</code> — nombre del grupo al que pertenece el host. Los grupos se listan en el array <code>groups</code> del nivel superior, con <code>name</code> y <code>color</code> (ARGB como entero de 32 bits con signo). Si un host hace referencia a un grupo que no está en el array, se crea automáticamente con un color por defecto — al escribir el archivo a mano puedes omitir el array.</li>
                <li><code>color</code> — color opcional del host (ARGB como entero de 32 bits con signo). Prevalece sobre el color del grupo.</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// CAMPOS OPCIONALES</div>
                Todos los campos excepto <code>label</code>, <code>hostname</code> y <code>username</code> son opcionales. Los campos omitidos adoptan sus valores por defecto.
            </div>`,
};

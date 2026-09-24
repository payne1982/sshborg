'use strict';
module.exports = {
  // ── index.html ─────────────────────────────────────────────────────────────
  page_title:        'SSHBorg – Android 与 iOS 的 SSH & SFTP 客户端',
  meta_description:  'SSHBorg 是一款功能强大的 SSH 和 SFTP 客户端，支持 Android、iPhone 和 iPad。使用密钥认证、加密存储和生物识别锁，安全地从手机管理您的服务器。',

  nav_features:  '功能',
  nav_security:  '安全',
  nav_guide:     '用户指南',
  nav_support:   '支持',
  nav_privacy:   '隐私政策',
  nav_tip:       '打赏',
  nav_contact:   '联系我们',

  hero_sub: '功能强大的 Android 与 iOS SSH &amp; SFTP 客户端。<br>直接从手机安全管理您的服务器。',

  badge_no_ads:      '无广告',
  badge_no_tracking: '无跟踪',
  badge_no_cloud:    '无云端',
  badge_free:        '免费',
  badge_android:     'Android 10+',
  badge_ios: 'iOS 16+',

  cta_download:   '下载',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',
  cta_appstore: 'App Store',

  features_title: '// 系统功能',

  feat_terminal_title: '完整 SSH 终端',
  feat_terminal_desc:  '支持 VT100/xterm 仿真、完整 UTF-8 和多个并发会话的交互式终端。',
  feat_sftp_title:     'SFTP 文件管理器',
  feat_sftp_desc:      '通过直观的文件管理器浏览、上传、下载、重命名和删除服务器文件。',
  feat_keys_title:     'SSH 密钥认证',
  feat_keys_desc:      '直接在设备上生成 Ed25519、ECDSA 和 RSA 密钥，无需密码。',
  feat_jump_title:     '跳板机支持',
  feat_jump_desc:      '通过一台或多台堡垒主机进行透明隧道连接，完整支持 SSH 代理转发。',
  feat_biometric_title:'应用锁',
  feat_biometric_desc: '用生物识别或设备密码锁定应用——在 Android 上还可以使用应用内 PIN／密码短语，即使在 Android TV 上也能使用。超时时间可自定义。',
  feat_multilingual_title: '多语言',
  feat_multilingual_desc:  '支持英语、意大利语、法语、德语、西班牙语、葡萄牙语、乌克兰语、俄语、中文和日语。',
  feat_theme_title:    '深色与浅色主题',
  feat_theme_desc:     '跟随系统主题，或手动选择。在任何光线条件下均清晰可读。',
  feat_sessions_title: '多会话',
  feat_sessions_desc:  '同时保持多个 SSH 和 SFTP 会话，即时切换。',

  security_title: '// 隐私优先，由设计保障',
  security_desc:  'SSHBorg 从不收集您的数据。一切留在您的设备上——您的凭据、密钥和连接。',

  sec_badge_keystore:   '设备硬件加密',
  sec_badge_analytics:  '无分析追踪',
  sec_badge_sdks:       '无第三方 SDK',
  sec_badge_screenshots:'屏幕内容保护',
  sec_badge_opensource: '开源 — GPL v3',

  security_pp_link: '阅读完整隐私政策 &rarr;',

  tip_title:  '// 打赏',
  tip_desc:   'SSHBorg 免费提供，无广告，无跟踪。如果它为您节省了时间，一点打赏可以让它持续下去。',
  kofi_cta:        '在 Ko-fi 上支持我',
  kofi_hero_cta:   '打赏我',
  support_title: '// 支持',
  support_desc: '发现了 bug 或有好点子？请在您所用应用的代码仓库中提交 issue——也可以直接给我写信。',
  support_issues_android: 'Android 问题反馈',
  support_issues_ios: 'iOS 问题反馈',

  footer_privacy: '隐私政策',
  footer_issues:  '问题与反馈',
  footer_source:  '源代码',
  footer_licenses: '许可证',
  footer_powered: 'SSH 连接由以下驱动',

  // ── docs.html ──────────────────────────────────────────────────────────────
  page_title_docs:       'SSHBorg – 用户指南',
  meta_description_docs: 'SSHBorg Android 与 iOS 用户指南：SSH 密钥、跳板机、代理转发、命令建议、tmux 等。',

  nav_home:           '主页',
  nav_getting_started:'快速入门',
  nav_ssh_keys:       'SSH 密钥',
  nav_jump_hosts:     '跳板机',
  nav_sftp:           'SFTP',
  nav_backup:         '备份',

  doc_page_title:    '// 用户指南',
  doc_page_subtitle: '操作指南——逐步指导，在 Android 和 iOS 上充分利用 SSHBorg。iOS 版有所不同之处，会以标有 iOS 的说明注明。',

  toc_title: '// 目录',

  doc_toc: `            <li><a href="#adding-host">添加主机</a></li>
            <li><a href="#host-groups">主机分组</a></li>
            <li class="sub"><a href="#host-groups">列表排序</a></li>
            <li><a href="#sftp">SFTP 文件管理器</a></li>
            <li class="sub"><a href="#sftp">浏览</a></li>
            <li class="sub"><a href="#sftp">上传与下载</a></li>
            <li class="sub"><a href="#sftp">批量选择</a></li>
            <li class="sub"><a href="#sftp">编辑文件</a></li>
            <li class="sub"><a href="#sftp">十六进制编辑器</a></li>
            <li><a href="#ssh-keys">SSH 密钥</a></li>
            <li class="sub"><a href="#ssh-keys">生成密钥</a></li>
            <li class="sub"><a href="#ssh-keys">在服务器授权</a></li>
            <li class="sub"><a href="#ssh-keys">密钥安全</a></li>
            <li><a href="#suggestions">命令建议</a></li>
            <li class="sub"><a href="#suggestions">工作原理</a></li>
            <li class="sub"><a href="#suggestions">故障排除</a></li>
            <li><a href="#terminal">终端手势</a></li>
            <li><a href="#terminal-settings">终端设置</a></li>
            <li><a href="#extra-keys">额外按键栏</a></li>
            <li><a href="#agent-forwarding">代理转发</a></li>
            <li><a href="#legacy-ciphers">旧版加密算法</a></li>
            <li><a href="#connection-drops">连接断开</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">跳板机</a></li>
            <li class="sub"><a href="#jump-hosts">多跳链</a></li>
            <li><a href="#sessions">多会话</a></li>
            <li><a href="#security">应用安全</a></li>
            <li><a href="#backup">配置备份</a></li>
            <li><a href="#android-tv">Android TV</a></li>
            <li><a href="#licenses">开源许可证</a></li>`,

  doc_adding_host: `
            <h2>// 添加主机</h2>
            <p>在主机列表界面点击 <strong>+</strong> 按钮添加新服务器（在 iOS 上会弹出一个小菜单：选择<em>添加主机</em>）。</p>
            <h3>必填字段</h3>
            <ul>
                <li><strong>主机名 / IP</strong> — 服务器地址或 IP，支持 IPv4 和 IPv6。</li>
                <li><strong>端口</strong> — 默认为 22。如果服务器使用其他端口运行 SSH，请修改此项。</li>
                <li><strong>用户名</strong> — 要登录的 Unix 用户（例如 <code>ubuntu</code>、<code>root</code>、<code>deploy</code>）。</li>
                <li><strong>认证方式</strong> — 选择密码或 SSH 密钥（推荐）。</li>
            </ul>
            <h3>主机指纹验证</h3>
            <p>首次连接时，SSHBorg 会显示服务器指纹并要求您确认。这是一项安全检查，确保您连接的是正确的服务器而非冒充者。接受前请通过可信渠道核实指纹与服务器管理员提供的一致。</p>
            <p>确认后，指纹将保存在本地。若日后连接时指纹发生变化，SSHBorg 将发出警告——这可能表示服务器重建、密钥轮换或中间人攻击。</p>
            <div class="callout callout-info">
                <div class="callout-label">// 提示</div>
                您可以随时用以下命令检查服务器指纹：
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_host_groups: `
            <h2>// 主机分组</h2>
            <p>当服务器列表越来越长时，可以把主机整理到可折叠的彩色分组中，例如<em>生产</em>、<em>家庭实验室</em>、<em>客户</em>。</p>
            <h3>创建分组</h3>
            <ol class="steps">
                <li>添加或编辑主机，打开<strong>分组</strong>下拉菜单。</li>
                <li>选择<strong>新建分组…</strong>，输入名称并从预设颜色中挑选一个。</li>
                <li>保存主机后，主机列表会将该分组显示为独立的区块。</li>
            </ol>
            <p>在 iOS 上也可以单独创建分组：点击 <strong>+</strong> 按钮 → <em>新建分组</em>。</p>
            <h3>使用分组</h3>
            <ul>
                <li><strong>折叠 / 展开</strong> — 点按分组标题即可折叠或重新展开。状态会被记住，重启应用后也不会丢失。</li>
                <li><strong>颜色</strong> — 分组颜色显示为标题上的圆点，并作为组内主机图标的着色。</li>
                <li><strong>编辑</strong> — 长按分组标题并选择<em>编辑</em>，可重命名分组或更换颜色。</li>
                <li><strong>删除</strong> — 长按标题并选择<em>删除</em>。组内的主机<em>不会</em>被删除，只是变为未分组。</li>
            </ul>
            <p>颜色可以从快捷色板中选择，在 Android 上也可以用渐变取色器自由调配——iOS 上从色板中选择。主机还可以拥有<strong>自己的颜色</strong>——在主机编辑器中、分组下方设置——它会覆盖分组颜色，对未分组的主机同样有效。</p>
            <p>未分组的主机始终显示在列表顶部；如果不创建任何分组，列表的外观和行为与以前完全相同。</p>
            <h3>列表排序</h3>
            <p>在<strong>设置 → 主机列表排序</strong>中选择列表的排列方式：</p>
            <ul>
                <li><strong>字母顺序</strong>（默认）——分组按名称，组内主机按标签。</li>
                <li><strong>最近使用</strong>——最后连接过的主机排在最前；从未使用过的排在最后。</li>
                <li><strong>最常使用</strong>——连接次数最多的主机排在前面。</li>
                <li><strong>手动</strong>——由你自己设定的顺序。</li>
            </ul>
            <p>前三种方式中分组始终保持字母顺序，因此分组标题不会移动，只有组内的主机重新排列。手动排序时分组也可以移动。</p>
            <p>要调整顺序，请选择<strong>手动</strong>，然后打开某个主机或分组标题的菜单——<strong>⋮</strong> 按钮（iOS 上为 <strong>…</strong>）、长按，或Android TV 遥控器上的<em>菜单</em>键——并使用<strong>上移</strong> / <strong>下移</strong>。主机只能在自己所属的区块内移动：要放到另一个分组，请在主机编辑器中更改分组。切换到手动时会完整保留当时屏幕上的顺序，因此不会发生跳动；之后新增的主机会排到所属区块的末尾。</p>`,

  doc_sftp: `
            <h2>// SFTP 文件管理器</h2>
            <p>SFTP 文件管理器让您直接从手机浏览、上传、下载、重命名和删除服务器上的文件。在主机列表界面，通过主机菜单中的<strong>文件</strong>打开它。</p>
            <h3>浏览</h3>
            <p>点击文件夹进入。使用返回箭头或点击路径栏中的任意段落跳转到上级目录。</p>
            <p>符号链接显示有小链接图标角标。点击符号链接会导航到其目标：若指向目录则进入，若指向文件则与普通文件相同。</p>
            <h3>上传文件</h3>
            <p>点击 <strong>上传</strong> 按钮（↑）从手机存储中选择一个或多个文件。上传立即开始，进度显示在界面顶部。</p>
            <h3>下载文件和文件夹</h3>
            <p>点击任意文件即可立即下载。要下载整个文件夹，点击其旁边的 <strong>下载</strong> 图标——SSHBorg 将下载整个目录树并保存到手机的 <strong>Downloads</strong> 文件夹。</p>
            <p>如果目标位置已存在同名文件，对话框将询问您是 <strong>覆盖</strong>、<strong>跳过</strong>该文件，还是 <strong>取消</strong>整个传输。</p>
            <div class="callout callout-info">
                <div class="callout-label">// 关于符号链接</div>
                文件夹下载时，指向目录的符号链接会被跳过——只下载普通文件（包括指向文件的符号链接）。这可防止意外的递归下载。
            </div>
            <h3>批量选择与操作</h3>
            <p>长按任意项目进入选择模式，然后继续点击其他项目以建立选择。工具栏显示当前选择的操作：</p>
            <ul>
                <li><strong>下载</strong> — 一次性下载所有选中的文件和文件夹，带进度对话框和取消支持。</li>
                <li><strong>删除</strong> — 删除所有选中的项目。删除非空文件夹会递归删除其所有内容。<em>此操作不可撤销。</em></li>
            </ul>
            <h3>编辑文件</h3>
            <p>打开文件菜单（长按，或 <strong>⋮</strong> 按钮），选择<strong>在编辑器中打开</strong>，即可直接在服务器上修改。手机上不留任何文件：内容读入内存、编辑后直接写回服务器。</p>
            <p>文件原样返回。字符编码会被识别并在保存时沿用，LF 或 CRLF 换行符保持不变，原本结尾没有换行的文件依旧没有，权限也不变。如果编码判断有误，点一下编辑器底部那一行换一个即可——列表里只有能够原样还原该文件字节的编码，所以选错只会显示错乱，不会损坏文件。</p>
            <p>保存时先在原文件旁写入临时文件，再改名顶替，因此连接中途断开也不会在服务器上留下写了一半的文件。</p>
            <p>文件最大可打开 4 MB，无论多长：只绘制屏幕上的行，因此几兆字节的文件与短文件滚动起来一样顺畅。再大的文件请用终端里的 <code>nano</code> 或 <code>vi</code>，那边没有任何上限。</p>
            <p>配置文件会着色——注释、字符串、数字、键、节标题、shell 变量和 XML 标签——依据文件名判断，文件名看不出时则依据内容的形态：shell 脚本、nginx 与 sshd 风格的配置、INI、YAML、JSON 和 XML。普通文章、笔记和日志则刻意不着色，那里的颜色只会变成干扰。着色只是外观，不会改变任何一个字节。</p>
            <h3>十六进制编辑器</h3>
            <p>非文本文件会在十六进制编辑器中打开：左侧是偏移量，中间是字节，右侧是可打印字符，底部是十六进制数字键盘。点选一个字节并输入两位数字即可替换。数值会变，长度不变，因此文件中每个偏移量都保持原位。文件菜单中的<strong>以十六进制打开</strong>可以用这种方式打开任何文件；被误判为二进制的文本文件（例如混入了一个 NUL 字节）仍可从同一个对话框以文本方式打开。</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                <ul>
                    <li><strong>上传</strong> — 打开<strong>操作</strong>菜单（⋯）并选择<em>上传…</em>；通过系统的“文件”浏览器选择文件。</li>
                    <li><strong>下载保存位置</strong> — 保存到 SSHBorg 自己的文件夹，“文件” App 中显示在<em>我的 iPhone → SSHBorg</em>（iPad 上为<em>我的 iPad</em>）。下载从不覆盖任何文件：若名称已被占用，新文件会得到一个带编号的名称。</li>
                    <li><strong>文件夹</strong> — 长按文件或文件夹打开其菜单：<em>下载</em>、<em>重命名</em>、<em>删除</em>。</li>
                    <li><strong>多选</strong> — 在“操作”菜单中选择<em>选择项目</em>，然后勾选项目。长按则会打开该项目的菜单。</li>
                    <li><strong>上传冲突</strong> — 对话框提供<em>覆盖</em>、<em>保留两者</em>（上传的文件会获得新名称）或<em>取消</em>。</li>
                    <li><strong>编辑</strong> — 文件编辑器和十六进制编辑器目前仅在 Android 上提供。</li>
                </ul>
            </div>`,

  doc_ssh_keys: `
            <h2>// SSH 密钥</h2>
            <p>基于密钥的认证比密码更安全，设置完成后无需记忆或输入任何内容。</p>
            <h3>生成密钥</h3>
            <p>点击主机列表界面顶部的<strong>钥匙</strong>图标打开 <strong>SSH 密钥</strong>，然后生成新密钥。SSHBorg 支持：</p>
            <ul>
                <li><strong>Ed25519</strong> — 推荐。快速、紧凑且安全。</li>
                <li><strong>ECDSA（P-256 / P-384）</strong> — 与旧版服务器有良好兼容性。</li>
                <li><strong>RSA（2048 / 4096 位）</strong> — 最大兼容性，但速度较慢。</li>
            </ul>
            <p>为密钥取一个有意义的名称（例如 <em>my-vps</em> 或 <em>work-server</em>），便于日后识别。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 安全说明</div>
                SSHBorg 有意不允许导出私钥。密钥永不离开设备。如果您需要在其他设备上使用，请在该设备上生成新密钥并分别在服务器上授权——这是更安全的做法。
            </div>
            <h3>在服务器上授权密钥</h3>
            <p>生成密钥后，点击密钥查看其 <strong>公钥</strong>。复制并粘贴到服务器上目标用户的 <code>~/.ssh/authorized_keys</code> 文件中。</p>
            <ol class="steps">
                <li>在手机上，打开 SSHBorg → SSH 密钥（主机列表界面的钥匙图标）→ 点击密钥 → 复制公钥。</li>
                <li>登录您的服务器（使用密码或已有密钥）。</li>
                <li>将公钥追加到授权密钥文件：
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...您复制的密钥..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>尝试用 SSHBorg 连接——应无需密码即可登录。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// 服务器要求</div>
                确保服务器的 <code>/etc/ssh/sshd_config</code> 中有 <code>PubkeyAuthentication yes</code>。大多数发行版默认启用，但部分加固镜像可能禁用此选项。
            </div>
            <h3>加密存储的密钥和密码</h3>
            <p>开启 <strong>设置 → 安全 → 加密敏感数据</strong>，即可使用保存在设备安全硬件中的密钥（Android 上为 Android 密钥库，iOS 上为钥匙串）加密存储私钥和已保存的主机密码。在您启用之前该选项保持关闭；首次启动时显示的安全提示会指向它。</p>
            <p>加密数据与设备以及本次安装的应用绑定：卸载应用后，已存储的密钥无法恢复，您需要生成新密钥并重新在服务器上授权。如果您在手机上保存了敏感的服务器凭据，强烈建议同时启用应用锁和此选项。</p>
            <p>受密码短语保护的密钥只在导入时询问一次密码短语：之后密码短语会与密钥一起保存，连接时不再询问；若已启用<strong>加密敏感数据</strong>，它会与密钥一同加密。在此之前导入的密钥需要重新导入——密钥列表会标出缺少密码短语的那些。</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                在 iOS 上，受密码短语保护的密钥暂时无法使用：导入时会校验密码短语，但不会保存，因此用该密钥连接会失败。请使用没有密码短语的密钥，或在应用内生成一个。
            </div>`,

  doc_suggestions: `
            <h2>// 命令建议</h2>
            <p>在终端输入时，SSHBorg 会在键盘上方显示建议栏。建议来自您所连接用户的 Shell 历史记录。</p>
            <h3>工作原理</h3>
            <p>终端会话开启时，SSHBorg 从远程服务器读取 Shell 历史文件，依次尝试以下位置：</p>
            <ol>
                <li><code>~/.bash_history</code> — Bash Shell 的默认位置</li>
                <li><code>~/.zsh_history</code> — Zsh 的默认位置（若设置了 <code>$HISTFILE</code> 也会检查）</li>
                <li><code>~/.local/share/fish/fish_history</code> — Fish Shell 用户</li>
            </ol>
            <p>使用第一个存在且可读的文件。输入时，命令会实时过滤并以标签形式显示在建议栏。点击标签即可插入命令。</p>
            <h3>故障排除</h3>
            <p><strong>没有建议出现</strong></p>
            <ul>
                <li>历史文件可能尚不存在（首次登录，或 Shell 未配置保存历史）。</li>
                <li>确保 Shell 已配置写入历史。对于 Bash，在 <code>~/.bashrc</code> 中添加：
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>对于 Zsh，在 <code>~/.zshrc</code> 中添加：
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>建议来自错误的用户</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// 已知限制</div>
                如果您以某个用户连接后运行 <code>sudo su - root</code>（或用 <code>su</code> 切换到其他用户），建议栏仍显示<em>原始登录用户</em>的历史，而非 <code>root</code> 的历史。这是因为 SSHBorg 在 Shell 启动前使用连接凭据读取历史文件。
                <br><br>
                若要获取 root 的命令建议，请在 SSHBorg 中单独添加配置为直接以 <code>root</code> 登录的主机条目（若服务器允许）。
            </div>`,

  doc_terminal: `
            <h2>// 终端手势</h2>
            <p>终端除键盘输入外，还响应以下触摸手势：</p>
            <ul>
                <li><strong>滚动历史</strong> — 上下滑动浏览终端回滚缓冲区。</li>
                <li><strong>缩放</strong> — 捏合手势放大或缩小文字。</li>
                <li><strong>复制文本</strong> — 长按终端任意位置进入选择模式。拖动控制柄调整选择区域，然后点击 <em>复制选中内容</em> 只复制高亮文本，或点击 <em>全部复制</em> 复制所有输出。点击其他位置取消。 在 iOS 上，长按会选中手指下的单词并打开系统菜单，其中有<em>拷贝</em>、<em>粘贴</em>和<em>全选</em>；拖动选区任一端可扩大选区。</li>
                <li><strong>粘贴</strong> — 使用键盘打开时可见的扩展键栏中的 <em>粘贴</em> 按钮。</li>
            </ul>
            <p><strong>全屏程序。</strong> 占据整个屏幕的程序 — tmux、vim、nano、less — 没有自己的回滚缓冲区，因此在它们运行期间，视图会停留在当前画面，而不会滑入下面的 shell 输出。</p>
            <p>要在其中滚动，需要由程序自己处理。在 tmux 中，将 <code>set -g mouse on</code> 加入 <code>~/.tmux.conf</code>：此后滑动即可翻阅手指所在窗格的历史。任何接受鼠标输入的程序同理，例如设置了 <code>set mouse=a</code> 的 vim。使用 <code>screen</code> 时，请检查 <code>~/.screenrc</code> 中是否有 <code>altscreen on</code> — 许多系统默认关闭它，此时 screen 会直接画在普通回滚缓冲区之上。</p>`,

  doc_terminal_settings: `
            <h2>// 终端设置</h2>
            <p>在<strong>设置 → 终端</strong>中可以按需调整终端：</p>
            <ul>
                <li><strong>终端配色</strong> — 经典<em>深色</em>方案（黑底白字）、在强光下更易读的<em>浅色</em>方案（白底黑字），或<em>跟随应用主题</em>（随应用深浅色主题自动切换）。更改立即生效，包括已打开的会话。</li>
                <li><strong>保持屏幕常亮</strong> — 终端打开时阻止屏幕熄灭。查看日志或运行长时间命令时很实用。默认关闭。</li>
                <li><strong>默认字体大小</strong> — 新终端会话的初始文字大小；每个会话中仍可用双指缩放。</li>
                <li><strong>回滚缓冲区</strong>、<strong>反向滚动</strong>和<strong>命令建议</strong> — 分别控制保留多少输出历史、滚动方向，以及上文介绍的建议栏。</li>
                <li><strong>双击操作</strong> — 可选：在终端双击时发送 <em>Tab</em>（自动补全），或连按两次 <em>Tab</em>（列出所有候选项）。默认关闭。 在 iOS 上，此选项关闭时双击会选中一个单词。</li>
            </ul>`,

  doc_extra_keys: `
            <h2>// 额外按键栏</h2>
            <p>软键盘打开时，上方会出现一排快捷按键。左右滑动按键栏可访问所有按键。</p>
            <h3>修饰键</h3>
            <p><strong>Ctrl</strong> 和 <strong>Alt</strong> 是粘滞切换键——点击其中一个，再点击字母键即可发送组合键。每次按键后自动重置。</p>
            <ul>
                <li><strong>Ctrl+C</strong> — 中断正在运行的进程。</li>
                <li><strong>Ctrl+D</strong> — 发送 EOF / 关闭 shell。</li>
                <li><strong>Ctrl+Z</strong> — 暂停进程。</li>
                <li><strong>Ctrl+L</strong> — 清屏。</li>
            </ul>
            <h3>文字模式</h3>
            <p>拼写检查图标可在<em>终端模式</em>和<em>文字模式</em>之间切换键盘。终端模式（默认）下自动更正和词语建议被禁用——适合输入命令和文件路径。文字模式下键盘像普通文本框一样工作，启用词语建议和自动更正。通过 SSH 输入自然语言时很有用，例如使用 Claude Code 或其他交互式工具。</p>
            <p>在 Android 上，文字模式也是语音输入的前提。键盘的麦克风键需要一个真正的文本框来写入，因此在终端模式下语音输入不会产生任何内容——听写前请切换到文字模式。</p>
            <h3>导航与编辑</h3>
            <ul>
                <li><strong>ESC</strong> — Escape 键。</li>
                <li><strong>Tab</strong> — shell 自动补全。</li>
                <li><strong>↑ ↓ ← →</strong> — 光标方向键。</li>
                <li><strong>Home / End</strong> — 跳到行首或行尾。</li>
                <li><strong>PgUp / PgDn</strong> — 向上翻页 / 向下翻页。</li>
                <li><strong>Del</strong> — 向前删除（光标右侧的字符）。</li>
                <li><strong>粘贴</strong> — 将剪贴板内容粘贴到终端。</li>
            </ul>
            <h3>功能键</h3>
            <p>向右滑动按键栏可访问 <strong>F1 至 F12</strong> 功能键。</p>
            <h3>布局与自定义按键栏</h3>
            <p>按键栏有多种布局。点按栏上的 <strong>⇄</strong> 键即可切换：按键会让位于可用按键栏列表，点按其中一个即可，选择会被记住。也可以在<strong>设置 → 终端 → 额外按键栏 → 自定义</strong>中选择和管理按键栏。</p>
            <ul>
                <li><strong>标准</strong> — 经典的单行滚动栏。</li>
                <li><strong>自然</strong> — 按使用频率排列的单行，包含 <code>/ - | ~</code>，方向键按键盘顺序排列（← ↑ ↓ →）。</li>
                <li><strong>自然 ×2</strong> — 两行铺满宽度：<code>ESC / - Home ↑ End PgUp</code> 在上，<code>Tab Ctrl Alt ← ↓ → PgDn</code> 在下。</li>
                <li><strong>自然 ×3</strong> — 同上，另加第三行功能键。</li>
                <li><strong>精简</strong> — 只有 ESC、Tab、Ctrl 和方向键，适合窄屏。</li>
            </ul>
            <p>要创建自己的按键栏，可复制一个预设（旁边的 <strong>⋮</strong> 菜单，iOS 上为长按）或新建一个。编辑器会按实际外观显示按键栏：点按一个按键将其选中，然后用下方按钮把它左右移动、移到另一行、更改、在其后添加按键或移除。每一行可以<em>滚动</em>（按键保持自然宽度）或<em>填满</em>屏幕宽度（手机上每行最多约九个按键）。最多三行；按键大小可调。</p>
            <p>除了常规按键，自定义按键栏还可以包含<strong>文本键</strong>：任意文本原样发送——单个 <code>|</code>、<code>sudo </code> 前缀或一整条命令。用 <code>\\n</code> 表示回车、<code>\\t</code> 表示 Tab、<code>\\e</code> 表示 Esc，这样 <code>ls -la\\n</code> 就成了一键宏。另有两个操作键：<strong>固定</strong>在键盘关闭时仍保持按键栏可见，<strong>键盘</strong>显示或隐藏键盘。</p>
            <p>自定义按键栏包含在设置备份中。在 Android TV 上一切都可用遥控器操作：编辑器的按键和按钮都能用方向键到达。</p>`,

  doc_agent_forwarding: `
            <h2>// 代理转发</h2>
            <p>SSH 代理转发允许 SSHBorg 中存储的密钥用于验证<em>从远程服务器内部</em>发起的进一步连接——例如 <code>git clone</code> 私有仓库，或跳转到第二台服务器。</p>
            <h3>在 SSHBorg 中启用转发</h3>
            <p>添加或编辑主机时，启用 <strong>代理转发</strong> 开关。SSHBorg 将作为该会话的 SSH 代理。</p>
            <h3>服务器端配置</h3>
            <p>服务器必须允许代理转发。检查 <code>/etc/ssh/sshd_config</code>：</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>大多数系统默认如此。修改后重启 SSH 守护进程：</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>每主机客户端配置（可选）</h3>
            <p>如果您也从笔记本或台式机连接此服务器，可在本地 <code>~/.ssh/config</code> 中持久配置转发：</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// 安全说明</div>
                代理转发使远程服务器暂时获得访问您的 SSH 代理套接字的权限。在会话活跃期间，该服务器上的 root 用户（或受攻击的进程）可能使用您的密钥连接其他地方。仅在您信任的服务器上启用转发。
            </div>`,

  doc_legacy_ciphers: `
            <h2>// 旧版加密算法支持</h2>
            <p>一些旧版服务器——网络设备、嵌入式设备或运行过时 OpenSSH 版本的系统——仅支持现代 SSH 客户端默认不再通告的加密算法。</p>
            <p>在主机设置中启用<strong>允许旧版加密</strong>开关后，SSHBorg 会将以下算法追加到协商列表：</p>
            <h3>新增算法</h3>
            <ul>
                <li><strong>加密算法：</strong> <code>aes128-cbc</code>、<code>aes192-cbc</code>、<code>aes256-cbc</code>、<code>3des-cbc</code></li>
                <li><strong>密钥交换：</strong> <code>diffie-hellman-group14-sha1</code>、<code>diffie-hellman-group-exchange-sha1</code>、<code>diffie-hellman-group1-sha1</code></li>
                <li><strong>主机密钥类型：</strong> <code>ssh-dss</code>（DSA 1024-bit）</li>
            </ul>
            <p>服务器始终协商双方都支持的最强算法，因此启用此选项不会削弱与现代服务器的连接安全性。</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                在 iOS 上无法使用 <code>ssh-dss</code> 主机密钥：应用所用的 SSH 库已不再包含 DSA，因此仅提供 DSA 主机密钥的服务器，即使开启此选项，也无法从 iPhone 或 iPad 连接。
            </div>
            <div class="callout callout-warn">
                <div class="callout-label">// 安全提示</div>
                此列表中的算法在密码学上被认为较弱。仅对无法升级的服务器启用此选项。
            </div>`,

  doc_connection_drops: `
            <h2>// 连接断开与终端复用器</h2>
            <p>SSH 是手机与服务器之间的实时 TCP 连接。一旦连接中断——哪怕只是一秒——会话及其中运行的一切都会丢失。</p>
            <h3>移动端连接断开的原因</h3>
            <p>移动网络特别容易断开连接，原因如下：</p>
            <ul>
                <li><strong>IP 地址变更</strong> — 出行或切换基站时，运营商可能分配新的公网 IP。由于 TCP 连接与 IP 地址绑定，现有 SSH 会话立即失效。</li>
                <li><strong>Wi-Fi ↔ 移动数据切换</strong> — 在 Wi-Fi 和移动数据之间切换会改变 IP 并中断所有开放的 TCP 连接。</li>
                <li><strong>空闲超时</strong> — 运营商和 NAT 路由器通常在几分钟后断开空闲连接。长时间运行但静默的会话（查看日志、等待输入）容易受此影响。</li>
                <li><strong>信号丢失</strong> — 隧道、地下车库或信号弱区域都可能短暂断网，足以终止会话。</li>
                <li><strong>Android 后台限制</strong> — 为了省电，Android 会限制应用在后台持续运行的时长。在后台累计约六小时后，系统会停止 SSHBorg 的会话；你会收到通知，重新打开应用即可重新连接。将应用切回前台会重置此限制。</li>
                <li><strong>iOS 会在后台挂起应用</strong> — SSHBorg 离开屏幕约三十秒后，iOS 会将其挂起，连接随之断开。回到应用时，SSHBorg 会自动重新连接（仅在未保存密码时才会询问密码），并在输出中标记该位置：这是一个新的 shell，旧 shell 中运行的内容已经丢失。在 iOS 上，终端复用器是在使用其他应用时让工作继续运行的唯一方法。</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                如果您在 SSH 终端中直接运行长时间命令（编译、备份、数据库迁移），连接断开时命令会立即被终止。任何未完成的工作都可能处于不一致状态。
            </div>
            <h3>解决方案：tmux 或 screen</h3>
            <p>终端复用器在<em>服务器上</em>运行持久会话，完全独立于 SSH 连接。连接断开时，会话及其中运行的一切继续运行。重新连接后，重新附加即可找到一切如常。</p>
            <p>这是从手机管理服务器最有用的习惯。</p>
            <h3>tmux 快速入门</h3>
            <p><code>tmux</code> 在大多数现代 Linux 发行版上可用，是推荐的选择。</p>
            <pre><code># 开始一个新命名会话
tmux new -s work

# 从会话分离（保持运行）
Ctrl+B，然后 D

# 列出正在运行的会话
tmux ls

# 重新附加到会话
tmux attach -t work

# 附加到最近的会话
tmux attach</code></pre>
            <h3>screen 快速入门</h3>
            <p><code>screen</code> 较旧，但在几乎所有 Unix 系统上都可用，包括可能未安装 tmux 的最小服务器镜像。</p>
            <pre><code># 开始一个新命名会话
screen -S work

# 从会话分离
Ctrl+A，然后 D

# 列出正在运行的会话
screen -ls

# 重新附加到会话
screen -r work</code></pre>
            <h3>移动端推荐工作流程</h3>
            <ol class="steps">
                <li>用 SSHBorg 连接服务器。</li>
                <li>立即开始或重新附加 tmux/screen 会话：<code>tmux attach || tmux new -s main</code></li>
                <li>在复用器内运行命令。</li>
                <li>若连接断开，重新连接即可——会话仍然存在。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// 提示</div>
                您可以在服务器的 <code>~/.bashrc</code> 或 <code>~/.zshrc</code> 中添加 <code>tmux attach || tmux new -s main</code>，这样每次通过 SSHBorg 登录时都会自动启动复用器会话。
            </div>`,

  doc_jump_hosts: `
            <h2>// 跳板机</h2>
            <p>跳板机（也称堡垒主机）是一台中间服务器，用于访问无法从互联网直接到达的目标服务器。SSHBorg 原生支持单跳和多跳链。</p>
            <h3>在 SSHBorg 中配置跳板机</h3>
            <ol class="steps">
                <li>在 SSHBorg 中将堡垒服务器添加为普通主机（例如 <em>bastion</em>）。</li>
                <li>将目标服务器添加为另一台主机。</li>
                <li>在目标主机设置中，将 <strong>跳板机</strong> 设为您创建的堡垒主机。</li>
                <li>在堡垒主机条目上启用 <strong>代理转发</strong>——这允许密钥通过堡垒转发到目标服务器进行认证。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// 工作原理</div>
                SSHBorg 先与堡垒建立 SSH 连接，然后通过它向目标服务器打开转发的 TCP 通道。您的私钥永不离开手机——堡垒只代理加密流。
            </div>
            <h3>多跳链</h3>
            <p>如果需要跳过多台中间服务器（例如 互联网 → 堡垒 → DMZ → 目标），为每跳创建一个条目并链接：</p>
            <ul>
                <li><strong>bastion</strong> — 无跳板机，启用代理转发</li>
                <li><strong>dmz</strong> — 跳板机 = bastion，启用代理转发</li>
                <li><strong>target</strong> — 跳板机 = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                代理转发必须在<em>每个中间跳</em>上启用，而不只是第一个。缺少代理转发会导致认证链断裂，最终服务器连接将失败并显示"permission denied"错误。
            </div>
            <h3>等效的手动配置（仅供参考）</h3>
            <p>桌面端 <code>~/.ssh/config</code> 中的等效配置如下：</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>使用此配置，笔记本上的 <code>ssh target</code> 会透明地跳过堡垒。</p>
            <h3>防火墙要求</h3>
            <ul>
                <li>您的手机必须能在 SSH 端口（通常为 22）上访问堡垒。</li>
                <li>堡垒必须能在其 SSH 端口上访问目标。</li>
                <li>目标<em>不需要</em>从手机直接可达。</li>
            </ul>`,

  doc_sessions: `
            <h2>// 多会话</h2>
            <p>SSHBorg 允许您同时保持多个 SSH 终端会话和 SFTP 文件管理器会话，甚至可以连接到不同的服务器。</p>
            <ul>
                <li>点击主机即可打开终端，或在主机菜单中选择<strong>文件</strong>打开文件管理器。如果该主机已有打开的会话，同一菜单还可以新建会话。</li>
                <li>使用终端下方的标签页在已开启的会话之间切换；拥有多个会话的主机会打开带编号的选择器。</li>
                <li>只要网络连接正常，会话在后台保持活跃——在 iOS 上只能维持很短时间，参见<a href="#connection-drops">连接断开</a>。</li>
                <li>主机列表在每台主机旁显示小角标，标明活跃的 SSH 和 SFTP 会话数量，方便一目了然。</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// 提示</div>
                长时间运行的命令（编译、备份、日志监控）在切换到其他会话时仍会继续运行。如果希望它们在 SSH 连接断开后也能继续，请在服务器端使用 <code>tmux</code> 或 <code>screen</code>。
            </div>`,

  doc_security: `
            <h2>// 应用安全</h2>
            <h3>应用锁</h3>
            <p>在 <strong>设置 → 安全 → 应用锁</strong> 中选择保护方式：<strong>无</strong>（默认）、<strong>仅生物识别</strong>（指纹或人脸解锁）、<strong>设备锁</strong>（除生物识别外，还可使用设备的 PIN、图案或密码），或应用内 <strong>PIN 或密码短语</strong>。启用锁定后，SSHBorg 在显示任何主机、凭据或会话数据前都会要求验证。</p>
            <p>应用内 <strong>PIN 或密码短语</strong> 可在任何设备上使用，即使没有生物识别硬件或系统屏幕锁——因此它是 Android TV 上的正确选择。更改或移除时会先要求输入当前的。忘记后无法找回：你需要清除应用数据或重新安装，因此请保留主机的备份。</p>
            <p>您可以设置不活动超时——在后台等待该时长后，应用会自动锁定。</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                应用锁提供<strong>无</strong>、<strong>仅生物识别</strong>（面容 ID 或触控 ID）和<strong>设备锁</strong>（设备密码或生物识别）。没有应用内 PIN 或密码短语。
            </div>
            <h3>截图保护</h3>
            <p>SSHBorg 默认阻止截图和录屏，防止敏感的终端内容通过最近应用界面或截屏工具泄露。</p>
            <p>如果需要截图（例如分享终端输出），可在 <strong>设置 → 安全 → 允许截图</strong> 中临时禁用截图保护。</p>
            <div class="callout callout-ios">
                <div class="callout-label">// iOS</div>
                iOS 不允许应用阻止截屏或录屏，因此没有此设置。启用应用锁后，SSHBorg 一离开屏幕就会遮盖其内容，主机和终端不会出现在 App 切换器中。
            </div>
            <h3>凭据存储</h3>
            <p>开启<strong>加密敏感数据</strong>后（参见<a href="#ssh-keys">SSH 密钥</a>），密码和私钥会使用保存在 <strong>Android 密钥库</strong>（Android 10+ 上由硬件支持）或 iOS <strong>钥匙串</strong>中、仅限本设备的密钥加密存储。凭据永不写入外部存储，也不会传输到任何地方。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 备份说明</div>
                由于加密密钥永远不会离开设备的安全硬件，密钥<strong>无法</strong>从云备份或设备备份中恢复，也不会自动迁移到新手机。更换设备前，请确保在新设备上生成新密钥并在所有服务器上授权。
            </div>`,

  doc_android_tv: `
            <h2>// ANDROID TV</h2>
            <p>SSHBorg 可在 Android TV 和 Google TV 上运行。可用遥控器的方向键操作，但由于这是一款文字较多的应用，外接实体键盘会带来很大便利。</p>
            <h3>使用遥控器导航</h3>
            <ul>
                <li><strong>方向键</strong> — 在行与控件之间移动焦点。</li>
                <li><strong>OK（中键）</strong> — 主操作：连接主机、打开文件夹、下载文件、展开分组。</li>
                <li><strong>返回</strong> — 上一级或退出当前界面。</li>
            </ul>
            <h3>打开某一行的菜单</h3>
            <p>要对某一行进行重命名、删除、编辑、复制或下载，请打开其菜单：在选中该行时按遥控器的 <strong>菜单（选项）键</strong>，或 <strong>长按 OK</strong>。单按 OK 执行主操作，而非打开菜单。</p>
            <h3>输入文字：请使用实体键盘</h3>
            <p>强烈建议使用 <strong>实体键盘（USB 或蓝牙</strong>，Android TV 两者都支持），终端基本上离不开它。有了键盘，文本框和 shell 都能正常使用。</p>
            <p>没有键盘时，屏幕键盘仍可用于简短输入：聚焦一个输入框，按 OK 打开，输入后按键盘上的 <strong>OK / 前往</strong> 确认——在密码提示框中这会直接连接。但用屏幕键盘输入 shell 命令并不实用。</p>
            <h3>在 TV 上锁定应用</h3>
            <p>TV 通常没有指纹识别或屏幕锁，因此请用内置的 <strong>PIN 或密码短语</strong> 锁保护应用（设置 → 安全）——它完全可用遥控器或键盘操作。参见 <a href="#security">应用安全</a>。</p>`,

  doc_licenses: `
            <h2>// 开源许可证</h2>
            <p>SSHBorg 是自由软件，以 <strong>GNU General Public License v3</strong> 发布。Android 版与 iOS 版的源代码都在 GitHub 上。它使用了以下库，各自遵循自己的许可证：</p>
            <ul>
                <li><a href="https://github.com/mwiede/jsch" target="_blank" rel="noopener"><strong>mwiede/JSch</strong></a> — Android 上的 SSH 协议 —— BSD 风格许可证</li>
                <li><a href="https://www.bouncycastle.org" target="_blank" rel="noopener"><strong>Bouncy Castle</strong></a> — Android 上的加密实现 —— MIT 风格许可证</li>
                <li><a href="https://github.com/Rosemoe/sora-editor" target="_blank" rel="noopener"><strong>sora-editor</strong></a> — 用于在服务器上修改文件的文本编辑控件 —— GNU LGPL v2.1</li>
                <li><a href="https://libssh2.org" target="_blank" rel="noopener"><strong>libssh2</strong></a> — iOS 上的 SSH 协议 —— BSD 许可证</li>
            </ul>
            <p>它们都不会自行连接任何地方：SSHBorg 只会连接你指定的服务器，以及你点击链接时的 sshborg.com。没有任何统计库、广告库或追踪器。</p>`,

  doc_backup: `
            <h2>// 配置备份</h2>
            <p>SSHBorg 可以将主机配置和应用设置导出并导入为 JSON 文件。这样您可以将服务器列表转移到另一台设备，或保留一份可移植的配置备份。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                备份包含主机配置和应用设置（终端、外观、行为）。<strong>密码和 SSH 密钥从不导出</strong> — 密钥材料需要在新设备上重新设置。不过备份会记录每个主机所用密钥的<em>名称</em>：如果在导入前重新创建同名密钥，其主机会自动重新关联。
            </div>
            <h3>导出</h3>
            <p>前往 <strong>设置 → 备份 → 导出备份</strong>。通过系统文件选择器选择文件保存位置。文件默认命名为 <code>sshborg_backup.json</code>（iOS 上为 <code>sshborg-backup-</code><em>日期</em><code>.json</code>）。</p>
            <h3>导入</h3>
            <p>前往 <strong>设置 → 备份 → 导入备份</strong>。选择之前导出（或手动创建）的 <code>.json</code> 文件。SSHBorg 将其与现有主机列表合并：</p>
            <ul>
                <li><strong>名称</strong>与现有条目匹配的主机将被<strong>更新</strong>。</li>
                <li>具有新名称的主机将被<strong>添加</strong>。</li>
                <li>文件中不存在的主机保持<strong>不变</strong>。</li>
                <li>更新已有主机时会保留其已保存的密码、密钥和已接受的主机密钥——备份从不包含这些内容。</li>
                <li>没有密钥的主机会关联到名称与导出的 <code>keyLabel</code> 匹配的密钥（若存在）；否则忽略该字段。</li>
                <li>被更新的主机会保留本设备已有的使用数据——最后连接时间、计数和手动位置；文件只补上缺少的部分。</li>
</ul>
            <h3>JSON 格式</h3>
            <p>导出文件是一个标准 JSON 对象。您也可以手动创建它，从其他来源批量导入服务器列表。</p>
            <pre><code>{
  "version": 7,
  "exported_at": "2026-05-14T10:00:00Z",
  "groups": [
    { "name": "生产", "color": -1754827, "position": 0 }
  ],
  "hosts": [
    {
      "label":           "我的 VPS",
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
      "keyLabel":         "我的 VPS 密钥",
      "group":           "生产"
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
            <p>可选的 <code>settings</code> 对象包含应用设置，导出时会自动写入；手动创建文件时可以省略。</p>
            <h3>字段说明</h3>
            <ul>
                <li><code>label</code> — SSHBorg 中显示的名称。导入时用作唯一合并键。<strong>必填。</strong></li>
                <li><code>hostname</code> — 服务器地址或 IP（IPv4 或 IPv6）。<strong>必填。</strong></li>
                <li><code>port</code> — SSH 端口。默认值：<code>22</code>。</li>
                <li><code>username</code> — 登录用户名。<strong>必填。</strong></li>
                <li><code>agentForwarding</code> — <code>true</code> 启用 SSH 代理转发。默认值：<code>false</code>。</li>
                <li><code>jumpMode</code> — <code>"simple"</code>（使用 <code>jumpHosts</code> 文本）或 <code>"host_list"</code>（使用 SSHBorg 内部主机 ID）。手动创建文件时请使用 <code>"simple"</code>。</li>
                <li><code>jumpHosts</code> — 以逗号分隔的跳板机列表，格式为 <code>[用户名@]主机[:端口]</code>。仅在 <code>jumpMode</code> 为 <code>"simple"</code> 时使用。</li>
                <li><code>portForwardings</code> — 以换行符分隔的本地端口转发规则，使用 SSH <code>-L</code> 语法，例如 <code>"8080:localhost:8080"</code>。</li>
                <li><code>sftpStartMode</code> — SFTP 起始目录：<code>"last"</code>（记住上次访问的目录）、<code>"fixed"</code>（始终使用 <code>sftpStartDir</code>）、<code>"home"</code>（服务器主目录）。默认值：<code>"last"</code>。</li>
                <li><code>sftpStartDir</code> — 当 <code>sftpStartMode</code> 为 <code>"fixed"</code> 时使用的路径。</li>
                <li><code>sftpShowHidden</code> — <code>true</code> 时在该主机的 SFTP 浏览器中显示隐藏文件（点文件，以 "." 开头的名称）。默认 <code>false</code>。</li>
                <li><code>allowLegacyCiphers</code> — 设为 <code>true</code> 可启用上文介绍的旧式加密算法。默认为 <code>false</code>。</li>
                <li><code>keyLabel</code> — 此主机所用 SSH 密钥的名称。导入时，如果存在同名密钥，则关联到该主机；否则忽略该字段。密钥本身从不包含在备份中。</li>
                <li><code>group</code> — 主机所属分组的名称。分组列在顶层的 <code>groups</code> 数组中，包含 <code>name</code> 和 <code>color</code>（ARGB，带符号 32 位整数）。如果主机引用的分组不在数组中，会用默认颜色自动创建 — 因此手写文件时可以完全省略该数组。</li>
                <li><code>color</code> — 主机的可选颜色（ARGB，带符号 32 位整数），会覆盖分组颜色。</li>
                            <li><code>position</code> — 主机在手动排序中的位置，按其所属区块（未分组区块或某个分组）内部计数。省略则排到末尾。分组在 <code>groups</code> 数组中也有各自的 <code>position</code>。</li>
                <li><code>lastConnected</code> — 最后一次连接的时间，以 Unix 纪元起的毫秒数表示。用于“最近使用”排序。</li>
                <li><code>connectCount</code> — 向该主机开启过多少个终端会话。用于“最常使用”排序。</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// 可选字段</div>
                除 <code>label</code>、<code>hostname</code> 和 <code>username</code> 外，所有字段均为可选。省略的字段将使用默认值。
            </div>`,

  // ── changelog.html ─────────────────────────────────────────────────────────
  nav_changelog:               '更新日志',
  page_title_changelog:        'SSHBorg – 更新日志',
  meta_description_changelog:  'SSHBorg {PLATFORM} 版的版本历史：每个版本的变化。',
  changelog_subtitle:          '所有已发布的版本，从最新开始。',
};

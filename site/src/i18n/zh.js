'use strict';
module.exports = {
  // ── index.html ─────────────────────────────────────────────────────────────
  page_title:        'SSHBorg – Android SSH & SFTP 客户端',
  meta_description:  'SSHBorg 是一款功能强大的 Android SSH 和 SFTP 客户端。使用密钥认证、加密存储和生物识别锁，安全地从手机管理您的服务器。',

  nav_features:  '功能',
  nav_security:  '安全',
  nav_guide:     '用户指南',
  nav_support:   '支持',
  nav_privacy:   '隐私政策',
  nav_tip:       '打赏',
  nav_contact:   '联系我们',

  hero_sub: '功能强大的 Android SSH &amp; SFTP 客户端。<br>直接从手机安全管理您的服务器。',

  badge_no_ads:      '无广告',
  badge_no_tracking: '无跟踪',
  badge_no_cloud:    '无云端',
  badge_free:        '免费',
  badge_android:     'Android 10+',

  cta_download:   '下载',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',

  features_title: '// 系统功能',

  feat_terminal_title: '完整 SSH 终端',
  feat_terminal_desc:  '支持 VT100/xterm 仿真、完整 UTF-8 和多个并发会话的交互式终端。',
  feat_sftp_title:     'SFTP 文件管理器',
  feat_sftp_desc:      '通过直观的文件管理器浏览、上传、下载、重命名和删除服务器文件。',
  feat_keys_title:     'SSH 密钥认证',
  feat_keys_desc:      '直接在设备上生成 Ed25519、ECDSA 和 RSA 密钥，无需密码。',
  feat_jump_title:     '跳板机支持',
  feat_jump_desc:      '通过一台或多台堡垒主机进行透明隧道连接，完整支持 SSH 代理转发。',
  feat_biometric_title:'生物识别锁',
  feat_biometric_desc: '通过指纹或人脸解锁保护服务器访问权限，超时时间可自定义。',
  feat_multilingual_title: '多语言',
  feat_multilingual_desc:  '支持英语、意大利语、法语、德语、西班牙语、葡萄牙语、乌克兰语、中文和日语。',
  feat_theme_title:    '深色与浅色主题',
  feat_theme_desc:     '跟随系统主题，或手动选择。在任何光线条件下均清晰可读。',
  feat_sessions_title: '多会话',
  feat_sessions_desc:  '同时保持多个 SSH 和 SFTP 会话，即时切换。',

  security_title: '// 隐私优先，由设计保障',
  security_desc:  'SSHBorg 从不收集您的数据。一切留在您的设备上——您的凭据、密钥和连接。',

  sec_badge_keystore:   'Android 密钥库加密',
  sec_badge_analytics:  '无分析追踪',
  sec_badge_sdks:       '无第三方 SDK',
  sec_badge_screenshots:'截图保护',
  sec_badge_opensource: '开源 — GPL v3',

  security_pp_link: '阅读完整隐私政策 &rarr;',

  tip_title:  '// 打赏',
  tip_desc:   'SSHBorg 免费提供，无广告，无跟踪。如果它为您节省了时间，一点打赏可以让它持续下去。',
  kofi_cta:        '在 Ko-fi 上支持我',
  kofi_hero_cta:   '打赏我',

  footer_privacy: '隐私政策',
  footer_issues:  '问题与反馈',
  footer_source:  '源代码',
  footer_powered: 'SSH 连接由以下驱动',

  // ── docs.html ──────────────────────────────────────────────────────────────
  page_title_docs:       'SSHBorg – 用户指南',
  meta_description_docs: 'SSHBorg 用户指南：SSH 密钥、跳板机、代理转发、命令建议、tmux 等。',

  nav_home:           '主页',
  nav_getting_started:'快速入门',
  nav_ssh_keys:       'SSH 密钥',
  nav_jump_hosts:     '跳板机',
  nav_sftp:           'SFTP',
  nav_backup:         '备份',

  doc_page_title:    '// 用户指南',
  doc_page_subtitle: '操作指南——逐步指导，充分利用 SSHBorg。',

  toc_title: '// 目录',

  doc_toc: `            <li><a href="#adding-host">添加主机</a></li>
            <li><a href="#sftp">SFTP 文件管理器</a></li>
            <li class="sub"><a href="#sftp">浏览</a></li>
            <li class="sub"><a href="#sftp">上传与下载</a></li>
            <li class="sub"><a href="#sftp">批量选择</a></li>
            <li><a href="#ssh-keys">SSH 密钥</a></li>
            <li class="sub"><a href="#ssh-keys">生成密钥</a></li>
            <li class="sub"><a href="#ssh-keys">在服务器授权</a></li>
            <li class="sub"><a href="#ssh-keys">密钥安全</a></li>
            <li><a href="#suggestions">命令建议</a></li>
            <li class="sub"><a href="#suggestions">工作原理</a></li>
            <li class="sub"><a href="#suggestions">故障排除</a></li>
            <li><a href="#terminal">终端手势</a></li>
            <li><a href="#extra-keys">额外按键栏</a></li>
            <li><a href="#agent-forwarding">代理转发</a></li>
            <li><a href="#connection-drops">连接断开</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">跳板机</a></li>
            <li class="sub"><a href="#jump-hosts">多跳链</a></li>
            <li><a href="#sessions">多会话</a></li>
            <li><a href="#security">应用安全</a></li>
            <li><a href="#backup">配置备份</a></li>`,

  doc_adding_host: `
            <h2>// 添加主机</h2>
            <p>在主机列表界面点击 <strong>+</strong> 按钮添加新服务器。</p>
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

  doc_sftp: `
            <h2>// SFTP 文件管理器</h2>
            <p>SFTP 文件管理器让您直接从手机浏览、上传、下载、重命名和删除服务器上的文件。在主机列表界面点击 <strong>SFTP</strong> 开启 SFTP 会话。</p>
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
            </ul>`,

  doc_ssh_keys: `
            <h2>// SSH 密钥</h2>
            <p>基于密钥的认证比密码更安全，设置完成后无需记忆或输入任何内容。</p>
            <h3>生成密钥</h3>
            <p>前往 <strong>设置 → SSH 密钥 → 生成新密钥</strong>。SSHBorg 支持：</p>
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
                <li>在手机上，打开 SSHBorg → 设置 → SSH 密钥 → 点击密钥 → 复制公钥。</li>
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
            <h3>密钥额外加密</h3>
            <p>SSHBorg 提供可选的 <strong>额外密码短语</strong>（设置 → SSH 密钥 → 点击密钥 → 启用加密）。启用后，密钥将使用 SSHBorg 不存储的密码短语加密——每次使用密钥时都需要输入。</p>
            <p>如果您在手机上存储了敏感服务器凭据，或已禁用生物识别锁，强烈建议启用此功能。</p>`,

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
                <li><strong>复制文本</strong> — 长按终端任意位置进入选择模式。拖动控制柄调整选择区域，然后点击 <em>复制选中内容</em> 只复制高亮文本，或点击 <em>全部复制</em> 复制所有输出。点击其他位置取消。</li>
                <li><strong>粘贴</strong> — 使用键盘打开时可见的扩展键栏中的 <em>粘贴</em> 按钮。</li>
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
            <p>向右滑动按键栏可访问 <strong>F1 至 F12</strong> 功能键。</p>`,

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
                <li>在主机列表界面点击 <strong>终端</strong> 或 <strong>SFTP</strong> 开启会话。</li>
                <li>使用界面顶部的会话选择器在已开启的会话之间切换。</li>
                <li>只要网络连接正常，会话在后台保持活跃。</li>
                <li>主机列表在每台主机旁显示小角标，标明活跃的 SSH 和 SFTP 会话数量，方便一目了然。</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// 提示</div>
                长时间运行的命令（编译、备份、日志监控）在切换到其他会话时仍会继续运行。如果希望它们在 SSH 连接断开后也能继续，请在服务器端使用 <code>tmux</code> 或 <code>screen</code>。
            </div>`,

  doc_security: `
            <h2>// 应用安全</h2>
            <h3>生物识别锁</h3>
            <p>在 <strong>设置 → 安全 → 生物识别锁</strong> 中启用。启用后，SSHBorg 在显示任何主机、凭据或会话数据前，都需要指纹或人脸解锁。</p>
            <p>您可以设置不活动超时——在后台等待该时长后，应用会自动锁定。</p>
            <h3>截图保护</h3>
            <p>SSHBorg 默认阻止截图和录屏，防止敏感的终端内容通过最近应用界面或截屏工具泄露。</p>
            <p>如果需要截图（例如分享终端输出），可在 <strong>设置 → 安全 → 允许截图</strong> 中临时禁用截图保护。</p>
            <h3>凭据存储</h3>
            <p>所有凭据（密码、私钥、密码短语）均使用 <strong>Android 密钥库</strong>（Android 10+ 提供的硬件支持的安全飞地）加密存储。它们永不写入外部存储或在任何地方传输。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 备份说明</div>
                由于密钥存储在 Android 密钥库中，它们<strong>无法</strong>通过 Android 云备份机制备份，也不会自动迁移到新手机。更换设备前，请确保在新设备上生成新密钥并在所有服务器上授权。
            </div>`,

  doc_backup: `
            <h2>// 配置备份</h2>
            <p>SSHBorg 可以将主机配置导出并导入为 JSON 文件。这样您可以将服务器列表转移到另一台设备，或保留一份可移植的配置备份。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                备份仅包含主机配置（地址、端口、用户名、设置）。<strong>密码和 SSH 密钥从不导出</strong> — 在新设备上导入后需要重新设置。
            </div>
            <h3>导出</h3>
            <p>前往 <strong>设置 → 备份 → 导出主机</strong>。通过系统文件选择器选择文件保存位置。文件默认命名为 <code>sshborg_hosts.json</code>。</p>
            <h3>导入</h3>
            <p>前往 <strong>设置 → 备份 → 导入主机</strong>。选择之前导出（或手动创建）的 <code>.json</code> 文件。SSHBorg 将其与现有主机列表合并：</p>
            <ul>
                <li><strong>名称</strong>与现有条目匹配的主机将被<strong>更新</strong>。</li>
                <li>具有新名称的主机将被<strong>添加</strong>。</li>
                <li>文件中不存在的主机保持<strong>不变</strong>。</li>
            </ul>
            <h3>JSON 格式</h3>
            <p>导出文件是一个标准 JSON 对象。您也可以手动创建它，从其他来源批量导入服务器列表。</p>
            <pre><code>{
  "version": 1,
  "exported_at": "2026-05-14T10:00:00Z",
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
      "sftpStartDir":    null
    }
  ]
}</code></pre>
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
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// 可选字段</div>
                除 <code>label</code>、<code>hostname</code> 和 <code>username</code> 外，所有字段均为可选。省略的字段将使用默认值。
            </div>`,
};

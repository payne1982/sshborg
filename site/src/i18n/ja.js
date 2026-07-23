'use strict';
module.exports = {
  // ── index.html ─────────────────────────────────────────────────────────────
  page_title:        'SSHBorg – Android SSH & SFTP クライアント',
  meta_description:  'SSHBorgはAndroid向けの強力なSSH/SFTPクライアントです。鍵認証、暗号化ストレージ、生体認証ロックでサーバーをスマートフォンから安全に管理できます。',

  nav_features:  '機能',
  nav_security:  'セキュリティ',
  nav_guide:     'ユーザーガイド',
  nav_support:   'サポート',
  nav_privacy:   'プライバシーポリシー',
  nav_tip:       '支援する',
  nav_contact:   'お問い合わせ',

  hero_sub: 'Android向け強力なSSH &amp; SFTPクライアント。<br>スマートフォンから直接、安全にサーバーを管理。',

  badge_no_ads:      '広告なし',
  badge_no_tracking: 'トラッキングなし',
  badge_no_cloud:    'クラウドなし',
  badge_free:        '無料',
  badge_android:     'Android 10+',

  cta_download:   'ダウンロード',
  cta_play:       'Google Play',
  cta_appgallery: 'AppGallery',
  cta_fdroid:     'F-Droid',

  features_title: '// システム機能',

  feat_terminal_title: '完全SSH端末',
  feat_terminal_desc:  'VT100/xtermエミュレーション、完全UTF-8サポート、複数の同時セッションに対応したインタラクティブ端末。',
  feat_sftp_title:     'SFTPファイルマネージャー',
  feat_sftp_desc:      '直感的なファイルマネージャーでサーバー上のファイルを参照、アップロード、ダウンロード、リネーム、削除。',
  feat_keys_title:     'SSH鍵認証',
  feat_keys_desc:      'Ed25519、ECDSA、RSA鍵をデバイス上で直接生成。パスワード不要。',
  feat_jump_title:     '踏み台ホスト対応',
  feat_jump_desc:      '1台以上の踏み台ホストを透過的なトンネルで経由して接続。SSHエージェント転送に完全対応。',
  feat_biometric_title:'生体認証ロック',
  feat_biometric_desc: '指紋や顔認証でサーバーへのアクセスを保護。タイムアウトは設定可能。',
  feat_multilingual_title: '多言語対応',
  feat_multilingual_desc:  '英語、イタリア語、フランス語、ドイツ語、スペイン語、ポルトガル語、ウクライナ語、中国語、日本語に対応。',
  feat_theme_title:    'ダーク＆ライトテーマ',
  feat_theme_desc:     'システムテーマに追従するか、手動で選択。どんな照明条件でも読みやすい。',
  feat_sessions_title: 'マルチセッション',
  feat_sessions_desc:  '複数のSSHおよびSFTPセッションを同時に保持し、即座に切り替え。',

  security_title: '// プライバシーファースト、設計から',
  security_desc:  'SSHBorgはあなたのデータを一切収集しません。認証情報、鍵、接続情報はすべてデバイス上に留まります。',

  sec_badge_keystore:   'Android Keystore暗号化',
  sec_badge_analytics:  '解析なし',
  sec_badge_sdks:       'サードパーティSDKなし',
  sec_badge_screenshots:'スクリーンショット保護',
  sec_badge_opensource: 'オープンソース — GPL v3',

  security_pp_link: '完全なプライバシーポリシーを読む &rarr;',

  tip_title:  '// 支援する',
  tip_desc:   'SSHBorgは広告もトラッキングもない無料アプリです。時間の節約に役立てば、少しのご支援で開発を続けられます。',
  kofi_cta:        'Ko-fiでサポートする',
  kofi_hero_cta:   '支援する',

  footer_privacy: 'プライバシーポリシー',
  footer_issues:  '問題・フィードバック',
  footer_source:  'ソースコード',
  footer_powered: 'SSH接続は以下で動作',

  // ── docs.html ──────────────────────────────────────────────────────────────
  page_title_docs:       'SSHBorg – ユーザーガイド',
  meta_description_docs: 'SSHBorgユーザーガイド：SSH鍵、踏み台ホスト、エージェント転送、コマンド候補、tmuxなど。',

  nav_home:           'ホーム',
  nav_getting_started:'はじめに',
  nav_ssh_keys:       'SSH鍵',
  nav_jump_hosts:     '踏み台ホスト',
  nav_sftp:           'SFTP',
  nav_backup:         'バックアップ',

  doc_page_title:    '// ユーザーガイド',
  doc_page_subtitle: '操作ガイド — SSHBorgを最大限に活用するための手順書。',

  toc_title: '// 目次',

  doc_toc: `            <li><a href="#adding-host">ホストの追加</a></li>
            <li><a href="#host-groups">ホストグループ</a></li>
            <li><a href="#sftp">SFTPファイルマネージャー</a></li>
            <li class="sub"><a href="#sftp">ナビゲーション</a></li>
            <li class="sub"><a href="#sftp">アップロード＆ダウンロード</a></li>
            <li class="sub"><a href="#sftp">複数選択</a></li>
            <li><a href="#ssh-keys">SSH鍵</a></li>
            <li class="sub"><a href="#ssh-keys">鍵の生成</a></li>
            <li class="sub"><a href="#ssh-keys">サーバーへの登録</a></li>
            <li class="sub"><a href="#ssh-keys">鍵のセキュリティ</a></li>
            <li><a href="#suggestions">コマンド候補</a></li>
            <li class="sub"><a href="#suggestions">仕組み</a></li>
            <li class="sub"><a href="#suggestions">トラブルシューティング</a></li>
            <li><a href="#terminal">端末ジェスチャー</a></li>
            <li><a href="#terminal-settings">ターミナル設定</a></li>
            <li><a href="#extra-keys">追加キーバー</a></li>
            <li><a href="#agent-forwarding">エージェント転送</a></li>
            <li><a href="#legacy-ciphers">レガシー暗号</a></li>
            <li><a href="#connection-drops">接続が切れる場合</a></li>
            <li class="sub"><a href="#connection-drops">tmux / screen</a></li>
            <li><a href="#jump-hosts">踏み台ホスト</a></li>
            <li class="sub"><a href="#jump-hosts">マルチホップチェーン</a></li>
            <li><a href="#sessions">複数セッション</a></li>
            <li><a href="#security">アプリのセキュリティ</a></li>
            <li><a href="#backup">設定のバックアップ</a></li>`,

  doc_adding_host: `
            <h2>// ホストの追加</h2>
            <p>ホスト画面の <strong>+</strong> ボタンをタップして新しいサーバーを追加します。</p>
            <h3>必須フィールド</h3>
            <ul>
                <li><strong>ホスト名 / IP</strong> — サーバーアドレスまたはIPアドレス。IPv4とIPv6の両方をサポート。</li>
                <li><strong>ポート</strong> — デフォルトは22。サーバーが別のポートでSSHを動かしている場合は変更してください。</li>
                <li><strong>ユーザー名</strong> — ログインするUnixユーザー（例：<code>ubuntu</code>、<code>root</code>、<code>deploy</code>）。</li>
                <li><strong>認証方式</strong> — パスワードまたはSSH鍵（推奨）から選択。</li>
            </ul>
            <h3>ホスト鍵フィンガープリントの確認</h3>
            <p>初回接続時にSSHBorgはサーバーのフィンガープリントを表示し、承認を求めます。これはセキュリティチェックです。接続先が正しいサーバーであり、なりすましでないことを確認します。承認前に、サーバー管理者から提供されたか信頼できる手段で確認したフィンガープリントと一致するかを確かめてください。</p>
            <p>承認後、フィンガープリントはローカルに保存されます。将来の接続でフィンガープリントが変わった場合、SSHBorgは警告を表示します。これはサーバーの再構築、鍵のローテーション、または中間者攻撃を示している可能性があります。</p>
            <div class="callout callout-info">
                <div class="callout-label">// ヒント</div>
                サーバーのフィンガープリントはいつでも以下のコマンドで確認できます：
                <pre><code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code></pre>
            </div>`,

  doc_host_groups: `
            <h2>// ホストグループ</h2>
            <p>サーバーの一覧が増えてきたら、ホストを折りたたみ可能な色分けグループに整理できます。例：<em>本番</em>、<em>自宅ラボ</em>、<em>クライアント</em>。</p>
            <h3>グループの作成</h3>
            <ol class="steps">
                <li>ホストを追加または編集し、<strong>グループ</strong>のドロップダウンを開きます。</li>
                <li><strong>新しいグループ…</strong>を選び、名前を入力して、あらかじめ用意された色から 1 つ選びます。</li>
                <li>ホストを保存すると、ホスト一覧にそのグループが独立したセクションとして表示されます。</li>
            </ol>
            <h3>グループの操作</h3>
            <ul>
                <li><strong>折りたたみ / 展開</strong> — グループのヘッダーをタップすると折りたたみ・再展開できます。状態は記憶され、アプリを再起動しても保持されます。</li>
                <li><strong>色</strong> — グループの色はヘッダーのドットとして表示され、グループ内のホストアイコンにも同じ色が付きます。</li>
                <li><strong>編集</strong> — ヘッダーを長押しして<em>編集</em>を選ぶと、グループ名の変更や色の変更ができます。</li>
                <li><strong>削除</strong> — ヘッダーを長押しして<em>削除</em>を選びます。グループ内のホストは削除<em>されず</em>、グループなしに戻るだけです。</li>
            </ul>
            <p>色はクイックスウォッチから選ぶことも、グラデーションのカラーピッカーで自由に作ることもできます。ホストには<strong>専用の色</strong>も設定でき（ホスト編集画面のグループのすぐ下）、グループの色より優先され、グループのないホストでも使えます。</p>
            <p>グループのないホストは一覧の先頭に表示されます。グループを作らなければ、一覧の見た目も動作も従来とまったく同じです。</p>`,

  doc_sftp: `
            <h2>// SFTPファイルマネージャー</h2>
            <p>SFTPファイルマネージャーを使うと、スマートフォンからサーバー上のファイルを直接参照、アップロード、ダウンロード、リネーム、削除できます。ホスト画面で <strong>SFTP</strong> をタップしてSFTPセッションを開きます。</p>
            <h3>ナビゲーション</h3>
            <p>フォルダをタップして開きます。戻る矢印またはパスバーの任意のセグメントをタップしてディレクトリツリーを上に移動します。</p>
            <p>シンボリックリンクは小さなリンクアイコンバッジで表示されます。シンボリックリンクをタップするとターゲットに移動します。ディレクトリを指す場合は中に入り、ファイルを指す場合は通常のファイルと同様に動作します。</p>
            <h3>ファイルのアップロード</h3>
            <p><strong>アップロード</strong>ボタン（↑）をタップして、スマートフォンのストレージから1つ以上のファイルを選択します。アップロードはすぐに開始され、進行状況が画面上部に表示されます。</p>
            <h3>ファイルとフォルダのダウンロード</h3>
            <p>ファイルをタップするとすぐにダウンロードされます。フォルダ全体をダウンロードするには、その横にある <strong>ダウンロード</strong>アイコンをタップします。SSHBorgはディレクトリツリー全体をダウンロードし、スマートフォンの <strong>Downloads</strong> フォルダに保存します。</p>
            <p>保存先に同名ファイルが既に存在する場合、ダイアログで <strong>上書き</strong>、ファイルの <strong>スキップ</strong>、または転送全体の <strong>キャンセル</strong> を選択できます。</p>
            <div class="callout callout-info">
                <div class="callout-label">// シンボリックリンクについて</div>
                フォルダのダウンロード中、ディレクトリを指すシンボリックリンクはスキップされます。ダウンロードされるのは通常ファイル（ファイルを指すシンボリックリンクを含む）のみです。これにより意図しない再帰的ダウンロードを防ぎます。
            </div>
            <h3>複数選択と一括操作</h3>
            <p>任意の項目を長押しして選択モードに入り、追加の項目をタップして選択を増やします。ツールバーに現在の選択に対する操作が表示されます：</p>
            <ul>
                <li><strong>ダウンロード</strong> — 選択したすべてのファイルとフォルダを一括ダウンロード。進行ダイアログとキャンセルをサポート。</li>
                <li><strong>削除</strong> — 選択したすべての項目を削除。空でないフォルダを削除するとすべての内容が再帰的に削除されます。<em>元に戻す方法はありません。</em></li>
            </ul>`,

  doc_ssh_keys: `
            <h2>// SSH鍵</h2>
            <p>鍵ベースの認証はパスワードよりも安全で、設定後は何も覚えたり入力したりする必要がありません。</p>
            <h3>鍵の生成</h3>
            <p><strong>設定 → SSH鍵 → 新しい鍵を生成</strong> に進みます。SSHBorgは以下をサポートします：</p>
            <ul>
                <li><strong>Ed25519</strong> — 推奨。高速、コンパクト、安全。</li>
                <li><strong>ECDSA（P-256 / P-384）</strong> — 古いサーバーとの互換性が良好。</li>
                <li><strong>RSA（2048 / 4096ビット）</strong> — 最大の互換性だが低速。</li>
            </ul>
            <p>後で識別しやすいよう、意味のある名前（例：<em>my-vps</em> や <em>work-server</em>）を付けてください。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// セキュリティについて</div>
                SSHBorgは意図的に秘密鍵のエクスポートを許可しません。鍵はデバイスから外に出ません。別のデバイスで同じ鍵が必要な場合は、そのデバイスで新しい鍵を生成し、サーバーに別途登録してください。これがより安全なアプローチです。
            </div>
            <h3>サーバーへの鍵の登録</h3>
            <p>鍵を生成したら、タップして <strong>公開鍵</strong> を表示します。コピーして、ログインしたいユーザーのサーバー上の <code>~/.ssh/authorized_keys</code> ファイルに貼り付けます。</p>
            <ol class="steps">
                <li>スマートフォンで、SSHBorg → 設定 → SSH鍵 → 鍵をタップ → 公開鍵をコピー。</li>
                <li>サーバーにログイン（パスワードまたは既存の鍵で）。</li>
                <li>公開鍵を認証済み鍵ファイルに追記：
                    <pre><code>mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo "ssh-ed25519 AAAA...コピーした鍵..." >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys</code></pre>
                </li>
                <li>SSHBorgで接続を試みます。パスワードなしでログインできるはずです。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// サーバー要件</div>
                サーバーの <code>/etc/ssh/sshd_config</code> に <code>PubkeyAuthentication yes</code> があることを確認してください。ほとんどのディストリビューションではデフォルトで有効ですが、一部のセキュア強化イメージでは無効になっている場合があります。
            </div>
            <h3>鍵の追加暗号化</h3>
            <p>SSHBorgはオプションで <strong>追加パスフレーズ</strong> を提供しています（設定 → SSH鍵 → 鍵をタップ → 暗号化を有効にする）。有効にすると、鍵はSSHBorgが保存しないパスフレーズで暗号化され、鍵を使用するたびに入力が求められます。</p>
            <p>スマートフォンに機密サーバー認証情報を保存している場合、または生体認証ロックを無効にしている場合は、強くお勧めします。</p>`,

  doc_suggestions: `
            <h2>// コマンド候補</h2>
            <p>端末で入力中、SSHBorgはキーボードの上に候補バーを表示します。候補は接続したユーザーのシェル履歴から取得されます。</p>
            <h3>仕組み</h3>
            <p>端末セッションが開かれると、SSHBorgはリモートサーバーからシェル履歴ファイルを読み込みます。以下の場所を順に試みます：</p>
            <ol>
                <li><code>~/.bash_history</code> — Bashシェルのデフォルト</li>
                <li><code>~/.zsh_history</code> — Zshのデフォルト（<code>$HISTFILE</code>が設定されている場合も確認）</li>
                <li><code>~/.local/share/fish/fish_history</code> — Fishシェルユーザー向け</li>
            </ol>
            <p>存在して読み取り可能な最初のファイルが使用されます。入力するとコマンドがリアルタイムでフィルタリングされ、候補バーにチップとして表示されます。チップをタップするとコマンドが挿入されます。</p>
            <h3>トラブルシューティング</h3>
            <p><strong>候補が表示されない</strong></p>
            <ul>
                <li>履歴ファイルがまだ存在しない可能性があります（初回ログイン、またはシェルが履歴を保存するよう設定されていない）。</li>
                <li>シェルが履歴を書き込むよう設定されていることを確認してください。Bashの場合、<code>~/.bashrc</code> に追加：
                    <pre><code>HISTFILE=~/.bash_history
HISTSIZE=10000
HISTFILESIZE=20000</code></pre>
                </li>
                <li>Zshの場合、<code>~/.zshrc</code> に追加：
                    <pre><code>HISTFILE=~/.zsh_history
HISTSIZE=10000
SAVEHIST=10000
setopt APPEND_HISTORY SHARE_HISTORY</code></pre>
                </li>
            </ul>
            <p><strong>候補が別のユーザーのものになる</strong></p>
            <div class="callout callout-warn">
                <div class="callout-label">// 既知の制限</div>
                あるユーザーで接続した後に <code>sudo su - root</code>（または <code>su</code> で別のユーザーに切り替え）を実行した場合、候補バーは <code>root</code> ではなく<em>元のログインユーザー</em>の履歴を表示し続けます。これは、SSHBorgがシェル起動前に接続時の認証情報を使って履歴ファイルを読み込むためです。
                <br><br>
                rootの履歴候補を取得するには、SSHBorgで直接 <code>root</code> としてログインするよう設定した別のホストエントリーを追加してください（サーバーが許可している場合）。
            </div>`,

  doc_terminal: `
            <h2>// 端末ジェスチャー</h2>
            <p>端末はタイピング以外にも、いくつかのタッチジェスチャーに対応しています：</p>
            <ul>
                <li><strong>履歴のスクロール</strong> — 上下にスワイプして端末のスクロールバックバッファを参照。</li>
                <li><strong>ズーム</strong> — ピンチ操作でフォントサイズを拡大・縮小。</li>
                <li><strong>テキストのコピー</strong> — 端末の任意の場所を長押しして選択モードに入ります。ハンドルをドラッグして選択範囲を調整し、<em>選択をコピー</em> でハイライトされたテキストのみコピー、または <em>すべてコピー</em> で全出力をコピー。他の場所をタップするとキャンセル。</li>
                <li><strong>貼り付け</strong> — キーボードが開いているときに表示される追加キーバーの <em>貼り付け</em> ボタンを使用。</li>
            </ul>`,

  doc_terminal_settings: `
            <h2>// ターミナル設定</h2>
            <p><strong>設定 → ターミナル</strong>で、ターミナルを好みに合わせて調整できます：</p>
            <ul>
                <li><strong>ターミナルの配色</strong> — 定番の<em>ダーク</em>配色（黒地に白）、日中の明るい場所でも読みやすい<em>ライト</em>配色（白地に黒）、またはアプリのテーマと連動して自動で切り替わる<em>アプリのテーマに合わせる</em>。変更は開いているセッションも含めて即座に反映されます。</li>
                <li><strong>画面をオンのまま維持</strong> — ターミナルを開いている間、画面が消灯しないようにします。ログの監視や長時間かかるコマンドに便利です。既定ではオフです。</li>
                <li><strong>既定のフォントサイズ</strong> — 新しいセッション開始時の文字サイズ。各セッションでのピンチズームは引き続き使えます。</li>
                <li><strong>スクロールバック</strong>、<strong>スクロール方向の反転</strong>、<strong>コマンド候補</strong> — 保持する出力履歴の量、スクロールの向き、前述の候補バーを制御します。</li>
            </ul>`,

  doc_extra_keys: `
            <h2>// 追加キーバー</h2>
            <p>ソフトウェアキーボードが開いているとき、その上にショートカットボタンの列が表示されます。バーを横にスクロールすると、すべてのキーにアクセスできます。</p>
            <h3>修飾キー</h3>
            <p><strong>Ctrl</strong> と <strong>Alt</strong> はスティッキートグルです。一方をタップしてから文字キーをタップすることで、組み合わせを送信できます。次のキー入力後に自動的にリセットされます。</p>
            <ul>
                <li><strong>Ctrl+C</strong> — 実行中のプロセスを中断します。</li>
                <li><strong>Ctrl+D</strong> — EOF を送信 / シェルを終了します。</li>
                <li><strong>Ctrl+Z</strong> — プロセスを一時停止します。</li>
                <li><strong>Ctrl+L</strong> — 画面をクリアします。</li>
            </ul>
            <h3>ワードモード</h3>
            <p>スペルチェックアイコンで、キーボードを<em>ターミナルモード</em>と<em>ワードモード</em>の間で切り替えられます。ターミナルモード（デフォルト）では自動修正と単語候補が無効になっており、コマンドやファイルパスの入力に最適です。ワードモードでは候補と自動修正が有効になり、通常のテキストフィールドと同様に動作します。SSH越しに自然言語を入力する際（Claude Code や他のインタラクティブツールの使用時など）に便利です。</p>
            <h3>ナビゲーションと編集</h3>
            <ul>
                <li><strong>ESC</strong> — Escape キー。</li>
                <li><strong>Tab</strong> — シェルの自動補完。</li>
                <li><strong>↑ ↓ ← →</strong> — カーソル矢印キー。</li>
                <li><strong>Home / End</strong> — 行頭または行末にジャンプ。</li>
                <li><strong>PgUp / PgDn</strong> — ページアップ / ページダウン。</li>
                <li><strong>Del</strong> — 前方削除（カーソルの右側の文字）。</li>
                <li><strong>貼り付け</strong> — クリップボードの内容をターミナルに貼り付けます。</li>
            </ul>
            <h3>ファンクションキー</h3>
            <p>バーを右にスクロールすると、<strong>F1 から F12</strong> のファンクションキーにアクセスできます。</p>`,

  doc_agent_forwarding: `
            <h2>// エージェント転送</h2>
            <p>SSHエージェント転送を使うと、SSHBorgに保存された鍵を、リモートサーバー<em>内部から</em>行われるさらなる接続の認証に使用できます。例えば、プライベートリポジトリの <code>git clone</code> や、2台目のサーバーへのホップなどです。</p>
            <h3>SSHBorgでの転送の有効化</h3>
            <p>ホストを追加または編集する際に、<strong>エージェント転送</strong>トグルを有効にします。SSHBorgはそのセッションのSSHエージェントとして機能します。</p>
            <h3>サーバー側の設定</h3>
            <p>サーバーはエージェント転送を許可している必要があります。<code>/etc/ssh/sshd_config</code> を確認：</p>
            <pre><code>AllowAgentForwarding yes</code></pre>
            <p>ほとんどのシステムではデフォルトです。変更後はSSHデーモンを再起動：</p>
            <pre><code>sudo systemctl restart sshd</code></pre>
            <h3>ホストごとのクライアント設定（オプション）</h3>
            <p>ラップトップやデスクトップからもこのサーバーに接続する場合、ローカルの <code>~/.ssh/config</code> で永続的に転送を設定できます：</p>
            <pre><code>Host myserver
    HostName 203.0.113.42
    User ubuntu
    ForwardAgent yes</code></pre>
            <div class="callout callout-warn">
                <div class="callout-label">// セキュリティについて</div>
                エージェント転送はリモートサーバーにSSHエージェントソケットへの一時的なアクセスを与えます。セッションがアクティブな間、そのサーバーのrootユーザー（または侵害されたプロセス）があなたの鍵を使って他の場所に接続できる可能性があります。信頼できるサーバーでのみ転送を有効にしてください。
            </div>`,

  doc_legacy_ciphers: `
            <h2>// レガシー暗号のサポート</h2>
            <p>一部の旧式サーバー——ネットワーク機器、組み込みデバイス、または古い OpenSSH バージョンを実行するシステム——は、現代の SSH クライアントがデフォルトでは提示しなくなった暗号アルゴリズムのみをサポートしています。</p>
            <p>ホスト設定で<strong>レガシー暗号を許可</strong>トグルを有効にすると、SSHBorg は以下のアルゴリズムをネゴシエーションリストに追加します：</p>
            <h3>追加されるアルゴリズム</h3>
            <ul>
                <li><strong>暗号：</strong> <code>aes128-cbc</code>、<code>aes192-cbc</code>、<code>aes256-cbc</code>、<code>3des-cbc</code></li>
                <li><strong>鍵交換：</strong> <code>diffie-hellman-group14-sha1</code>、<code>diffie-hellman-group-exchange-sha1</code>、<code>diffie-hellman-group1-sha1</code></li>
                <li><strong>ホスト鍵タイプ：</strong> <code>ssh-dss</code>（DSA 1024-bit）</li>
            </ul>
            <p>サーバーは常に双方がサポートする最強のアルゴリズムをネゴシエートするため、このオプションを有効にしても現代のサーバーへの接続のセキュリティは低下しません。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// セキュリティ注意</div>
                このリストのアルゴリズムは暗号学的に弱いとみなされています。アップグレードできないサーバーにのみこのオプションを有効にしてください。
            </div>`,

  doc_connection_drops: `
            <h2>// 接続が切れる場合とターミナルマルチプレクサー</h2>
            <p>SSHはスマートフォンとサーバー間のリアルタイムTCP接続です。接続が中断されると、たとえ一瞬でも、セッションとその中で実行されているすべてが失われます。</p>
            <h3>モバイルで接続が切れる理由</h3>
            <p>モバイルネットワークは特に接続が切れやすく、理由は以下の通りです：</p>
            <ul>
                <li><strong>IPアドレスの変更</strong> — 移動中や基地局の切り替え時に、キャリアが新しいパブリックIPを割り当てる場合があります。TCP接続はIPアドレスに紐付いているため、既存のSSHセッションは直ちに無効になります。</li>
                <li><strong>Wi-Fi ↔ モバイルデータの切り替え</strong> — Wi-FiとモバイルデータネットワークのIPが変わり、開いているTCP接続がすべて切断されます。</li>
                <li><strong>アイドルタイムアウト</strong> — キャリアやNATルーターは数分後にアイドル接続を切断することがよくあります。長時間実行されているが無音のセッション（ログ監視、入力待ち）がこれに脆弱です。</li>
                <li><strong>電波の喪失</strong> — トンネル、地下駐車場、または単に弱い電波でも一時的にネットワークが切断され、セッションが終了するのに十分です。</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                SSH端末で長時間コマンド（ビルド、バックアップ、データベースマイグレーション）を直接実行中に接続が切れると、コマンドは即座に強制終了されます。部分的な作業が不整合な状態になる可能性があります。
            </div>
            <h3>解決策：tmuxまたはscreen</h3>
            <p>ターミナルマルチプレクサーは<em>サーバー上で</em>永続的なセッションを実行し、SSH接続から完全に独立しています。接続が切れても、セッションとその中で実行されているすべてが継続されます。再接続したとき、セッションに再アタッチすると、すべてがそのままになっています。</p>
            <p>これはスマートフォンからサーバーを管理する上で最も有用な習慣です。</p>
            <h3>tmuxクイックスタート</h3>
            <p><code>tmux</code>はほとんどの最新Linuxディストリビューションで利用可能で、推奨の選択肢です。</p>
            <pre><code># 新しい名前付きセッションを開始
tmux new -s work

# セッションからデタッチ（実行を継続）
Ctrl+B、その後 D

# 実行中のセッションを一覧表示
tmux ls

# セッションに再アタッチ
tmux attach -t work

# 最新のセッションに再アタッチ
tmux attach</code></pre>
            <h3>screenクイックスタート</h3>
            <p><code>screen</code>は古いですが、tmuxがインストールされていない可能性がある最小サーバーイメージを含む、ほぼすべてのUnixシステムで利用できます。</p>
            <pre><code># 新しい名前付きセッションを開始
screen -S work

# セッションからデタッチ
Ctrl+A、その後 D

# 実行中のセッションを一覧表示
screen -ls

# セッションに再アタッチ
screen -r work</code></pre>
            <h3>モバイルでの推奨ワークフロー</h3>
            <ol class="steps">
                <li>SSHBorgでサーバーに接続。</li>
                <li>すぐにtmux/screenセッションを開始または再アタッチ：<code>tmux attach || tmux new -s main</code></li>
                <li>マルチプレクサー内でコマンドを実行。</li>
                <li>接続が切れても、再接続するだけ — セッションはまだそこにあります。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// ヒント</div>
                サーバーの <code>~/.bashrc</code> または <code>~/.zshrc</code> に <code>tmux attach || tmux new -s main</code> を追加すると、SSHBorgでログインするたびにマルチプレクサーセッションが自動的に開始されます。
            </div>`,

  doc_jump_hosts: `
            <h2>// 踏み台ホスト</h2>
            <p>踏み台ホスト（踏み台サーバーとも呼ばれます）は、インターネットから直接アクセスできないターゲットサーバーに到達するために経由する中間サーバーです。SSHBorgはシングルおよびマルチホップチェーンをネイティブにサポートしています。</p>
            <h3>SSHBorgでの踏み台ホストの設定</h3>
            <ol class="steps">
                <li>踏み台サーバーをSSHBorgに通常のホストとして追加します（例：<em>bastion</em>）。</li>
                <li>ターゲットサーバーを別のホストとして追加します。</li>
                <li>ターゲットホストの設定で、<strong>踏み台ホスト</strong>を作成した踏み台に設定します。</li>
                <li>踏み台エントリーで<strong>エージェント転送</strong>を有効にします。これにより鍵が踏み台を通してターゲットサーバーへの認証に転送されます。</li>
            </ol>
            <div class="callout callout-info">
                <div class="callout-label">// 仕組み</div>
                SSHBorgはまず踏み台へのSSH接続を確立し、次にそれを通してターゲットサーバーへの転送TCPチャンネルを開きます。秘密鍵はスマートフォンから外に出ません — 踏み台は暗号化されたストリームをプロキシするだけです。
            </div>
            <h3>マルチホップチェーン</h3>
            <p>複数の中間サーバーを経由する必要がある場合（例：インターネット → 踏み台 → DMZ → ターゲット）、各ホップのエントリーを作成してチェーンします：</p>
            <ul>
                <li><strong>bastion</strong> — 踏み台なし、エージェント転送オン</li>
                <li><strong>dmz</strong> — 踏み台 = bastion、エージェント転送オン</li>
                <li><strong>target</strong> — 踏み台 = dmz</li>
            </ul>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                エージェント転送は最初のホップだけでなく、<em>すべての中間ホップ</em>で有効にする必要があります。これなしでは認証チェーンが壊れ、最終サーバーへの接続が「permission denied」エラーで失敗します。
            </div>
            <h3>同等の手動設定（参考）</h3>
            <p>デスクトップの <code>~/.ssh/config</code> での同等の設定は次のようになります：</p>
            <pre><code>Host bastion
    HostName bastion.example.com
    User admin
    ForwardAgent yes

Host target
    HostName 10.0.1.50
    User ubuntu
    ProxyJump bastion
    ForwardAgent yes</code></pre>
            <p>この設定で、ラップトップからの <code>ssh target</code> は透過的に踏み台を経由します。</p>
            <h3>ファイアウォール要件</h3>
            <ul>
                <li>スマートフォンはSSHポート（通常22）で踏み台に到達できる必要があります。</li>
                <li>踏み台はSSHポートでターゲットに到達できる必要があります。</li>
                <li>ターゲットはスマートフォンから直接到達できる必要は<em>ありません</em>。</li>
            </ul>`,

  doc_sessions: `
            <h2>// 複数セッション</h2>
            <p>SSHBorgでは、異なるサーバーへのものも含め、複数のSSH端末セッションとSFTPファイルマネージャーセッションを同時に開いておくことができます。</p>
            <ul>
                <li>ホスト画面で <strong>端末</strong> または <strong>SFTP</strong> をタップしてセッションを開きます。</li>
                <li>画面上部のセッションセレクターを使って、開いているセッション間を切り替えます。</li>
                <li>ネットワーク接続が保たれている限り、セッションはバックグラウンドで生き続けます。</li>
                <li>ホストリストでは、各ホストの隣にアクティブなSSHおよびSFTPセッション数の小さなバッジが表示されるので、一目で開いているものを確認できます。</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// ヒント</div>
                長時間実行コマンド（ビルド、バックアップ、ログ監視）は別のセッションに切り替えても実行し続けます。SSH接続が切れても継続させたい場合は、サーバー側で <code>tmux</code> または <code>screen</code> を使用してください。
            </div>`,

  doc_security: `
            <h2>// アプリのセキュリティ</h2>
            <h3>生体認証ロック</h3>
            <p><strong>設定 → セキュリティ → 生体認証ロック</strong> で有効にします。有効にすると、SSHBorgはホスト、認証情報、セッションデータを表示する前に指紋または顔認証を要求します。</p>
            <p>非アクティブタイムアウトを設定できます。バックグラウンドでその時間が経過すると、アプリは自動的にロックされます。</p>
            <h3>スクリーンショット保護</h3>
            <p>SSHBorgはデフォルトでスクリーンショットと画面録画をブロックし、機密性の高い端末内容が最近使ったアプリの画面やスクリーンキャプチャツールから漏れないようにします。</p>
            <p>スクリーンショットが必要な場合（例：端末出力の共有）は、<strong>設定 → セキュリティ → スクリーンショットを許可</strong> で一時的に無効にできます。</p>
            <h3>認証情報の保存</h3>
            <p>すべての認証情報（パスワード、秘密鍵、パスフレーズ）は <strong>Android Keystore</strong>（Android 10+で利用可能なハードウェアバックアップされたセキュアエンクレーブ）を使用して暗号化して保存されます。外部ストレージへの書き込みや、どこかへの送信は一切行われません。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// バックアップについて</div>
                鍵はAndroid Keystoreに保存されているため、Androidのクラウドバックアップ機能でバックアップ<strong>できず</strong>、新しいスマートフォンに自動的に転送されません。デバイスを切り替える前に、新しいデバイスで生成した新しい鍵をすべてのサーバーに登録してください。
            </div>`,

  doc_backup: `
            <h2>// 設定のバックアップ</h2>
            <p>SSHBorgはホストの設定をJSONファイルとしてエクスポート・インポートできます。これにより、サーバーリストを別のデバイスに移したり、設定のポータブルなバックアップを保管したりできます。</p>
            <div class="callout callout-warn">
                <div class="callout-label">// 重要</div>
                バックアップにはホスト設定（アドレス、ポート、ユーザー名、設定）のみが含まれます。<strong>パスワードとSSH鍵はエクスポートされません</strong> — 新しいデバイスにインポートした後、再設定が必要です。
            </div>
            <h3>エクスポート</h3>
            <p><strong>設定 → バックアップ → ホストをエクスポート</strong> に移動します。システムのファイル選択ツールを使って保存先を選択します。ファイル名はデフォルトで <code>sshborg_hosts.json</code> です。</p>
            <h3>インポート</h3>
            <p><strong>設定 → バックアップ → ホストをインポート</strong> に移動します。以前エクスポートした（または手動で作成した）<code>.json</code> ファイルを選択します。SSHBorgは既存のホストリストと統合します：</p>
            <ul>
                <li><strong>名前</strong>が既存のエントリと一致するホストは<strong>更新</strong>されます。</li>
                <li>新しい名前を持つホストは<strong>追加</strong>されます。</li>
                <li>ファイルにないホストは<strong>変更されません</strong>。</li>
            </ul>
            <h3>JSON形式</h3>
            <p>エクスポートファイルは通常のJSONオブジェクトです。手動で作成して、別のソースからサーバーリストを一括インポートすることもできます。</p>
            <pre><code>{
  "version": 2,
  "exported_at": "2026-05-14T10:00:00Z",
  "groups": [
    { "name": "本番", "color": -1754827 }
  ],
  "hosts": [
    {
      "label":           "マイVPS",
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
      "group":           "本番"
    }
  ]
}</code></pre>
            <h3>フィールドリファレンス</h3>
            <ul>
                <li><code>label</code> — SSHBorgに表示される名前。インポート時の統合に使われる一意のキー。<strong>必須。</strong></li>
                <li><code>hostname</code> — サーバーのアドレスまたはIP（IPv4またはIPv6）。<strong>必須。</strong></li>
                <li><code>port</code> — SSHポート。デフォルト：<code>22</code>。</li>
                <li><code>username</code> — ログインユーザー名。<strong>必須。</strong></li>
                <li><code>agentForwarding</code> — <code>true</code> でSSHエージェント転送を有効化。デフォルト：<code>false</code>。</li>
                <li><code>jumpMode</code> — <code>"simple"</code>（<code>jumpHosts</code>のテキストを使用）または <code>"host_list"</code>（SSHBorgの内部ホストIDを使用）。手動作成時は <code>"simple"</code> を使用してください。</li>
                <li><code>jumpHosts</code> — <code>[ユーザー@]ホスト[:ポート]</code> 形式のカンマ区切りの踏み台ホスト。<code>jumpMode</code> が <code>"simple"</code> のときのみ使用。</li>
                <li><code>portForwardings</code> — SSH <code>-L</code> 構文による改行区切りのローカルポート転送ルール（例：<code>"8080:localhost:8080"</code>）。</li>
                <li><code>sftpStartMode</code> — SFTPの開始ディレクトリ：<code>"last"</code>（最後に訪問したディレクトリを記憶）、<code>"fixed"</code>（常に <code>sftpStartDir</code> を使用）、<code>"home"</code>（サーバーのホームディレクトリ）。デフォルト：<code>"last"</code>。</li>
                <li><code>sftpStartDir</code> — <code>sftpStartMode</code> が <code>"fixed"</code> のときに使用するパス。</li>
                <li><code>allowLegacyCiphers</code> — <code>true</code> にすると、前述のレガシー暗号アルゴリズムが有効になります。既定は <code>false</code> です。</li>
                <li><code>group</code> — ホストが属するグループ名。グループはトップレベルの <code>groups</code> 配列に <code>name</code> と <code>color</code>（符号付き 32 ビット整数の ARGB）で記載します。配列にないグループをホストが参照している場合は既定の色で自動作成されるため、手書きの場合は配列を丸ごと省略してもかまいません。</li>
                <li><code>color</code> — ホストごとの任意の色（符号付き 32 ビット整数の ARGB）。グループの色より優先されます。</li>
            </ul>
            <div class="callout callout-info">
                <div class="callout-label">// 省略可能なフィールド</div>
                <code>label</code>、<code>hostname</code>、<code>username</code> 以外のフィールドはすべて省略可能です。省略されたフィールドはデフォルト値が使用されます。
            </div>`,
};

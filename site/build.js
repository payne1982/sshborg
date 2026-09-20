#!/usr/bin/env node
'use strict';

const fs   = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

// ── Configuration ──────────────────────────────────────────────────────────────
const BASE_URL = 'https://sshborg.com';

// App Store page. The number is the app's Apple ID, shown in App Store Connect
// under App Information; the link only resolves once the app is released.
const APP_STORE_URL = 'https://apps.apple.com/app/id6811622926';

// The two apps live in separate repositories, each with its own issue tracker.
const REPO_ANDROID = 'https://github.com/payne1982/sshborg';
const REPO_IOS     = 'https://github.com/payne1982/sshborg-ios';

const LANGUAGES = [
  { code: 'en', hreflang: 'en',      dir: '',   root: '',    htmlLang: 'en',      ogLocale: 'en_US', fdroid: 'en'      },
  { code: 'it', hreflang: 'it',      dir: 'it', root: '../', htmlLang: 'it',      ogLocale: 'it_IT', fdroid: 'it'      },
  { code: 'de', hreflang: 'de',      dir: 'de', root: '../', htmlLang: 'de',      ogLocale: 'de_DE', fdroid: 'de'      },
  { code: 'es', hreflang: 'es',      dir: 'es', root: '../', htmlLang: 'es',      ogLocale: 'es_ES', fdroid: 'es'      },
  { code: 'fr', hreflang: 'fr',      dir: 'fr', root: '../', htmlLang: 'fr',      ogLocale: 'fr_FR', fdroid: 'fr'      },
  { code: 'pt', hreflang: 'pt',      dir: 'pt', root: '../', htmlLang: 'pt',      ogLocale: 'pt_PT', fdroid: 'pt_PT'   },
  { code: 'uk', hreflang: 'uk',      dir: 'uk', root: '../', htmlLang: 'uk',      ogLocale: 'uk_UA', fdroid: 'uk'      },
  { code: 'ru', hreflang: 'ru',      dir: 'ru', root: '../', htmlLang: 'ru',      ogLocale: 'ru_RU', fdroid: 'ru'      },
  { code: 'zh', hreflang: 'zh-Hans', dir: 'zh', root: '../', htmlLang: 'zh-Hans', ogLocale: 'zh_CN', fdroid: 'zh_Hans' },
  { code: 'ja', hreflang: 'ja',      dir: 'ja', root: '../', htmlLang: 'ja',      ogLocale: 'ja_JP', fdroid: 'ja'      },
];

// Pages to generate in all languages (privacy_policy stays English-only).
// The two changelog pages share one template and differ only in the platform they read.
const PAGES = ['index', 'docs', 'changelog', 'changelog-ios'];

const CHANGELOG_PAGES = {
  'changelog':     { template: 'changelog', platform: 'Android', other: 'changelog-ios.html', otherPlatform: 'iOS'     },
  'changelog-ios': { template: 'changelog', platform: 'iOS',     other: 'changelog.html',     otherPlatform: 'Android' },
};

// Play/App Store metadata directory (and release-note tag) per site language.
const STORE_LOCALE = {
  en: 'en-US', it: 'it-IT', de: 'de-DE', es: 'es-ES', fr: 'fr-FR',
  pt: 'pt-PT', uk: 'uk',    ru: 'ru-RU', zh: 'zh-CN', ja: 'ja-JP',
};

const ENDONYMS = {
  en: 'English',
  it: 'Italiano',
  de: 'Deutsch',
  es: 'Español',
  fr: 'Français',
  pt: 'Português',
  uk: 'Українська',
  ru: 'Русский',
  zh: '中文',
  ja: '日本語',
};

// ── Paths ──────────────────────────────────────────────────────────────────────
const SITE_DIR      = __dirname;
const ANDROID_REPO  = path.join(SITE_DIR, '..');
// The iOS app is a separate repository, a sibling of this one. Read-only, and optional: without
// it the iOS changelog page is simply left as it is.
const IOS_REPO      = path.join(SITE_DIR, '..', '..', 'claude-sshborg-ios');
const TEMPLATES_DIR = path.join(SITE_DIR, 'src', 'templates');
const I18N_DIR      = path.join(SITE_DIR, 'src', 'i18n');

// ── Helpers ────────────────────────────────────────────────────────────────────
function pageUrl(page, lang) {
  if (page === 'index') {
    return lang.dir ? `${BASE_URL}/${lang.dir}/` : `${BASE_URL}/`;
  }
  const file = `${page}.html`;
  return lang.dir ? `${BASE_URL}/${lang.dir}/${file}` : `${BASE_URL}/${file}`;
}

function hreflangBlock(page) {
  const lines = LANGUAGES.map(l =>
    `    <link rel="alternate" hreflang="${l.hreflang}" href="${pageUrl(page, l)}">`
  );
  lines.push(`    <link rel="alternate" hreflang="x-default" href="${pageUrl(page, LANGUAGES[0])}">`);
  return lines.join('\n');
}

function langSwitcher(page, currentLang) {
  const filename = `${page}.html`;
  const items = LANGUAGES
    .filter(l => l.code !== currentLang.code)
    .map(l => {
      const href = currentLang.root + (l.dir ? l.dir + '/' : '') + filename;
      return `<a href="${href}">${ENDONYMS[l.code]}</a>`;
    })
    .join('\n            ');
  return `<div class="lang-switcher">
        <span class="lang-current">${currentLang.code.toUpperCase()} &#9660;</span>
        <div class="lang-dropdown">
            ${items}
        </div>
    </div>`;
}

function applyVars(template, vars) {
  return Object.entries(vars).reduce(
    (t, [k, v]) => t.split(`{{${k}}}`).join(v ?? ''),
    template
  );
}

// ── Releases, for the changelog pages ─────────────────────────────────────────
// A version appears only once it is tagged in its repository, so an unreleased changelog sitting
// in the working tree never reaches the site. The tag gives the version number and its date; the
// text comes from the current branch, so a later wording fix shows up without moving the tag.

function git(repo, args) {
  return execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
}

/** Version tags of a repo, newest first. */
function releaseTags(repo) {
  const out = git(repo, ['for-each-ref', '--sort=-creatordate',
                         '--format=%(refname:short)\t%(creatordate:short)', 'refs/tags/v*']);
  return out.split('\n').filter(Boolean).map(line => {
    const [tag, date] = line.split('\t');
    return { tag, version: tag.replace(/^v/, ''), date };
  });
}

/** One locale's changelog text to a list of bullets. */
function bullets(text) {
  return text.split('\n').map(l => l.replace(/^\s*[•\-*]\s*/, '').trim()).filter(Boolean);
}

/** A release-notes file holding every locale in <xx-XX> blocks, to bullets per store locale. */
function parseLocalisedNotes(text) {
  const notes = {};
  const block = /<([A-Za-z-]+)>\n([\s\S]*?)\n<\/\1>/g;
  let m;
  while ((m = block.exec(text))) notes[m[1]] = bullets(m[2]);
  return notes;
}

/** Keeps a release only when English is there: nothing is published half-translated. */
function withNotes(rel, notes) {
  return notes.en && notes.en.length ? { ...rel, notes } : null;
}

// Android: one file per locale, named after the versionCode, which is read from the tag itself.
function androidReleases() {
  return releaseTags(ANDROID_REPO).map(rel => {
    const vc = (git(ANDROID_REPO, ['show', `${rel.tag}:app/build.gradle.kts`])
                  .match(/versionCode\s*=\s*(\d+)/) || [])[1];
    if (!vc) return null;
    const notes = {};
    for (const lang of LANGUAGES) {
      const file = path.join(ANDROID_REPO, 'fastlane', 'metadata', 'android',
                             STORE_LOCALE[lang.code], 'changelogs', `${vc}.txt`);
      if (fs.existsSync(file)) notes[lang.code] = bullets(fs.readFileSync(file, 'utf8'));
    }
    return withNotes(rel, notes);
  }).filter(Boolean);
}

// iOS: one file per version holding every locale. Read from that repo's V1 branch, so an
// unreleased draft on a development branch stays off the site.
function iosReleases() {
  if (!fs.existsSync(path.join(IOS_REPO, '.git'))) return [];
  return releaseTags(IOS_REPO).map(rel => {
    let text = null;
    try { text = git(IOS_REPO, ['show', `V1:release_notes/release_notes_v${rel.version}.txt`]); } catch { return null; }
    const byLocale = parseLocalisedNotes(text);
    const notes = {};
    for (const lang of LANGUAGES) {
      const got = byLocale[STORE_LOCALE[lang.code]];
      if (got) notes[lang.code] = got;
    }
    return withNotes(rel, notes);
  }).filter(Boolean);
}

function escapeHtml(s) {
  return s.replace(/&(?!(?:[a-zA-Z]+|#\d+);)/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function renderReleases(releases, lang) {
  return releases.map(r => {
    const items = (r.notes[lang.code] || r.notes.en)
      .map(b => `                <li>${escapeHtml(b)}</li>`).join('\n');
    return `        <article class="release">
            <h2>${escapeHtml(r.version)}<span class="release-date">${r.date}</span></h2>
            <ul>
${items}
            </ul>
        </article>`;
  }).join('\n');
}

// ── Build ──────────────────────────────────────────────────────────────────────
function build() {
  const sitemapPages = new Set();
  let built = 0;

  const releases = { Android: androidReleases(), iOS: iosReleases() };
  for (const [platform, list] of Object.entries(releases)) {
    console.log(`  •  ${platform}: ${list.length} released version${list.length === 1 ? '' : 's'}`);
  }

  for (const lang of LANGUAGES) {
    if (lang.dir) fs.mkdirSync(path.join(SITE_DIR, lang.dir), { recursive: true });

    // Reload translation module each run (supports watch mode)
    const i18nPath = require.resolve(path.join(I18N_DIR, `${lang.code}.js`));
    delete require.cache[i18nPath];
    const tr = require(i18nPath);

    for (const page of PAGES) {
      const cl = CHANGELOG_PAGES[page];
      // No tagged release for that platform (or no iOS repo here): leave its page untouched.
      if (cl && releases[cl.platform].length === 0) continue;

      const tplPath = path.join(TEMPLATES_DIR, `${cl ? cl.template : page}.html`);
      const tpl     = fs.readFileSync(tplPath, 'utf8');

      const canonical = `    <link rel="canonical" href="${pageUrl(page, lang)}">`;
      let   html      = tpl.replace('<!--HREFLANG-->', `${hreflangBlock(page)}\n${canonical}`);

      const clVars = cl ? {
        PLATFORM:        cl.platform,
        CHANGELOG_TITLE: `${tr.page_title_changelog} (${cl.platform})`,
        CHANGELOG_DESC:  tr.meta_description_changelog.split('{PLATFORM}').join(cl.platform),
        CHANGELOG_H1:    `${tr.nav_changelog} — ${cl.platform}`,
        OTHER_PAGE:      cl.other,
        OTHER_LABEL:     `${tr.nav_changelog} — ${cl.otherPlatform}`,
        RELEASES:        renderReleases(releases[cl.platform], lang),
      } : {};

      html = applyVars(html, { ...tr, ...clVars, LANG: lang.htmlLang, ROOT: lang.root, LANG_SWITCHER: langSwitcher(page, lang), CANONICAL_URL: pageUrl(page, lang), OG_LOCALE: lang.ogLocale, FDROID_URL: `https://f-droid.org/${lang.fdroid}/packages/com.sshborg/`, APP_STORE_URL, REPO_ANDROID, REPO_IOS });

      const outFile = path.join(SITE_DIR, lang.dir, `${page}.html`);
      fs.writeFileSync(outFile, html, 'utf8');
      console.log(`  ✓  ${(lang.dir ? lang.dir + '/' : '').padEnd(4)}${page}.html`);
      sitemapPages.add(page);
      built++;
    }
  }

  // ── sitemap.xml ─────────────────────────────────────────────────────────────
  let urlset = '';
  for (const page of sitemapPages) {
    const alts = LANGUAGES.map(l =>
      `      <xhtml:link rel="alternate" hreflang="${l.hreflang}" href="${pageUrl(page, l)}"/>`
    ).join('\n') + '\n' +
    `      <xhtml:link rel="alternate" hreflang="x-default" href="${pageUrl(page, LANGUAGES[0])}"/>`;
    for (const lang of LANGUAGES) {
      urlset += `  <url>\n    <loc>${pageUrl(page, lang)}</loc>\n${alts}\n  </url>\n`;
    }
  }
  // Privacy policy (English-only)
  urlset += `  <url>\n    <loc>${BASE_URL}/privacy_policy.html</loc>\n  </url>\n`;

  const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urlset}</urlset>`;

  fs.writeFileSync(path.join(SITE_DIR, 'sitemap.xml'), sitemap, 'utf8');
  console.log(`  ✓  sitemap.xml`);

  console.log(`\nBuild complete — ${built} pages generated.\n`);
}

build();

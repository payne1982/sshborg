#!/usr/bin/env node
'use strict';

const fs   = require('fs');
const path = require('path');

// ── Configuration ──────────────────────────────────────────────────────────────
const BASE_URL = 'https://sshborg.com';

const LANGUAGES = [
  { code: 'en', hreflang: 'en', dir: '',   root: '',    htmlLang: 'en', ogLocale: 'en_US' },
  { code: 'it', hreflang: 'it', dir: 'it', root: '../', htmlLang: 'it', ogLocale: 'it_IT' },
  { code: 'de', hreflang: 'de', dir: 'de', root: '../', htmlLang: 'de', ogLocale: 'de_DE' },
  { code: 'es', hreflang: 'es', dir: 'es', root: '../', htmlLang: 'es', ogLocale: 'es_ES' },
  { code: 'fr', hreflang: 'fr', dir: 'fr', root: '../', htmlLang: 'fr', ogLocale: 'fr_FR' },
  { code: 'pt', hreflang: 'pt', dir: 'pt', root: '../', htmlLang: 'pt', ogLocale: 'pt_PT' },
  { code: 'uk', hreflang: 'uk', dir: 'uk', root: '../', htmlLang: 'uk', ogLocale: 'uk_UA' },
];

// Pages to generate in all languages (privacy_policy stays English-only)
const PAGES = ['index', 'docs'];

const ENDONYMS = {
  en: 'English',
  it: 'Italiano',
  de: 'Deutsch',
  es: 'Español',
  fr: 'Français',
  pt: 'Português',
  uk: 'Українська',
};

// ── Paths ──────────────────────────────────────────────────────────────────────
const SITE_DIR      = __dirname;
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

// ── Build ──────────────────────────────────────────────────────────────────────
function build() {
  const sitemapPages = new Set();
  let built = 0;

  for (const lang of LANGUAGES) {
    if (lang.dir) fs.mkdirSync(path.join(SITE_DIR, lang.dir), { recursive: true });

    // Reload translation module each run (supports watch mode)
    const i18nPath = require.resolve(path.join(I18N_DIR, `${lang.code}.js`));
    delete require.cache[i18nPath];
    const tr = require(i18nPath);

    for (const page of PAGES) {
      const tplPath = path.join(TEMPLATES_DIR, `${page}.html`);
      const tpl     = fs.readFileSync(tplPath, 'utf8');

      const canonical = `    <link rel="canonical" href="${pageUrl(page, lang)}">`;
      let   html      = tpl.replace('<!--HREFLANG-->', `${hreflangBlock(page)}\n${canonical}`);

      html = applyVars(html, { ...tr, LANG: lang.htmlLang, ROOT: lang.root, LANG_SWITCHER: langSwitcher(page, lang), CANONICAL_URL: pageUrl(page, lang), OG_LOCALE: lang.ogLocale });

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

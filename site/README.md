# Simple Skin — website

The marketing and documentation site for the mod. Plain HTML, CSS and JavaScript with **no build
step**, so it deploys as static files anywhere.

## Deploying to Vercel

1. Import the repository at [vercel.com/new](https://vercel.com/new).
2. Set **Root Directory** to `site`.
3. Framework preset: **Other**. Leave the build and output commands empty.
4. Deploy.

`vercel.json` handles the rest — clean URLs (`/features` instead of `/features.html`), the
correct `Content-Type` and `Content-Disposition` for the mod jar so it downloads instead of
opening, cache headers for assets, and a few security headers.

To deploy from the CLI instead:

```bash
cd site
npx vercel --prod
```

## Local preview

Any static server works, for example:

```bash
cd site
python3 -m http.server 8000
```

Then open <http://localhost:8000>.

## Layout

```
site/
├── index.html          landing page — hero, slideshow, highlights, download
├── features.html       "View more" target — full feature list, install, usage
├── faq.html            questions, safety and troubleshooting
├── terms.html          licence, acceptable use, privacy
├── vercel.json         routing, headers, clean URLs
├── robots.txt          crawler rules
├── sitemap.xml         page index
├── downloads/
│   └── simple-skin-2.0.0.jar
└── assets/
    ├── css/style.css   one stylesheet, palette taken from the mod's SimpleSkinTheme
    ├── js/main.js      slideshow, download dialog, scroll reveal, mobile nav
    └── img/            logo sizes and the six showcase illustrations
```

## Updating the download

When a new mod version is built, drop the jar into `downloads/` and update the file name in the
four HTML pages (search for `simple-skin-2.0.0.jar`) plus the version label on the download
buttons.

## About the showcase pictures

`assets/img/slide-*.svg` are **interface illustrations, not screenshots**. They are generated from
the mod's real layout constants and its `SimpleSkinTheme` colours, so they match what the screens
draw, but they were not captured from a running game. Replace them with real screenshots once the
mod has been run.

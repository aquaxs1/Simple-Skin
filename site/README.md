# Simple Skin — website

The site at <https://simple-skin.vercel.app>. Plain HTML, CSS and JavaScript: no build
step, no framework, nothing loaded from another host.

| File | What it is |
| --- | --- |
| `index.html` | Landing page |
| `features.html` | Features |
| `setup.html` | Setup |
| `faq.html` | FAQ |
| `download.html` | Download |
| `terms.html` | Terms of Use |
| `404.html` | Shown for any address that does not exist |
| `assets/site.css` | This project's accent colour and anything only this site needs |
| `assets/scene.js` | The 3D scene in the page header |
| `assets/aqx/` | The look and behaviour shared by all AquAxs project sites |

## The shared part

`assets/aqx/` is the same in every project site: the stylesheet (`aqx.css`),
the interactions (`aqx.js`), the 3D stage (`stage.js`), three.js and the three
typefaces. It follows the portfolio at aqua.is-a.dev: a black sky with slow
dots, glass cards that lean towards the pointer, Bodoni Moda headlines.
Change the accent in `assets/site.css` (`--ac`, `--ac-hi`), not in `aqx.css`.

## The 3D scene

`assets/scene.js` exports one function that builds its objects into
`stage.frame`. The stage (`assets/aqx/stage.js`) does the rest: camera, lights,
resizing, pointer and touch, pausing off screen. With reduced motion the scene
is one still frame; without WebGL a glow in the accent colour stays instead.

## Preview

Links have no `.html` (Vercel serves `/download` from `download.html`), so open
the folder through a server that does the same:

```bash
npx serve site
```

## Deploy

Vercel, root directory `site`, no build command. `vercel.json` sets clean URLs
and the security headers. The site sets no cookies and stores nothing.

## Updating the download

The jar is not hosted here: the download buttons link to the
[GitHub releases](https://github.com/aquaxs1/Simple-Skin/releases/latest). After publishing a
new release, only update the version on the home page: the two download buttons (search
`index.html` for `simple-skin-2.0.0.jar` and `Download v2.0.0`) and `softwareVersion` in the
JSON-LD block at the top of the same file.

## About the showcase pictures

`assets/img/slide-*.svg` are **interface illustrations, not screenshots**. They are generated from
the mod's real layout constants and its `SimpleSkinTheme` colours, so they match what the screens
draw, but they were not captured from a running game. Replace them with real screenshots once the
mod has been run.

The skins the 3D model wears (`assets/scene.js`) are made up for this page.

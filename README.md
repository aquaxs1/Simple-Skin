# Simple Skin

Simple Skin is a Fabric mod for **Minecraft 26.2**. Press `K` in a world or on a
server to open a skin library with history, saved skins, the server player list,
a search box, and a small editor.

The mod is client-side. Installing it on a **server** as well is optional and
unlocks live skin sync (see below).

## Features

- Equip a stored skin immediately on the local client.
- Make the change visible to **other players**, either instantly through live
  sync on a Simple Skin server, or through the profile-upload route anywhere else.
- Steal skins from players on the server, or look **any player up by name**.
- Import skin PNGs from your computer, or just drop them in a folder.
- Outfits: pair a skin with a cape choice (keep, hide, or one your account owns).
- Edit a skin: remove second-layer overlays, graft on another skin's head, or
  replace one colour with another.
- Hotkeys per skin, a shuffle key, and optional automatic rotation.
- Rotate a large 3D preview, export any skin as PNG, and delete ones you don't want.

## How other players see your skin

There are two routes, and the mod picks the best one available.

**1. Live sync (instant, needs the mod on the server).** The client sends the
skin over a `simple_skin:equip` plugin channel; the server validates it and
relays it to every other Simple Skin client, which applies it right away. No
rejoin, no Mojang round-trip. On a vanilla server the channel is never
negotiated, so nothing is sent and the mod falls back to route 2.

**2. Profile upload (works everywhere, slower).** This is the only route a
purely client-side mod has. Every other player's game asks *the server* what
your skin is, and the server asks Mojang's session server exactly once — while
you are logging in. So:

1. **Upload.** The PNG is pushed to your authenticated Minecraft profile.
2. **Verify.** Simple Skin polls your profile and downloads the texture Mojang
   is actually serving, comparing it byte for byte. The CDN lags behind the
   upload, and rejoining too early would hand the server your *previous* skin.
3. **Rejoin.** Once Mojang serves the new skin, reconnecting makes the server
   re-read your profile and send the new texture to everyone online.

The `Visibility` setting controls route 2:

| Setting | What happens |
| --- | --- |
| `Only me` | Local render only. Nothing leaves your machine. |
| `Upload to profile` (default) | Uploads and verifies; you press **Rejoin now** when it suits you. |
| `Upload + rejoin` | Uploads, verifies, and reconnects automatically. |

Once the profile genuinely carries the skin, the local override is dropped, so
what you see is exactly what everybody else sees.

Uploading needs a real online session. In offline mode, or with an expired
token, the skin still applies locally and the status line says so instead of
pretending it worked.

## Capes

Mojang only lets an account activate a cape it already owns — capes cannot be
uploaded, by this mod or any other. An outfit can therefore keep the current
cape, hide it, or switch to one of your owned capes. That change goes to the
profile, so other players see it the same way they see a new skin.

## Folders

| Path | What it holds |
| --- | --- |
| `.minecraft/config/simple-skin/` | Library metadata and settings |
| `.minecraft/config/simple-skin/skins/` | Stored skin PNGs |
| `.minecraft/config/simple-skin/import/` | Drop PNGs here and they are imported |
| `.minecraft/simple-skin/exports/` | PNG exports |

## Build

Minecraft 26.2 needs **Java 25** and is shipped unobfuscated, so there are no
yarn or official mappings for it — Mojang stopped publishing `client_mappings`
with 26.1. The build uses Fabric's **no-remap Loom**, which compiles directly
against the shipped names, which is also why mod dependencies are declared as
ordinary `implementation` entries rather than `modImplementation`.

Run `./gradlew build` (`gradlew.bat build` on Windows) with a Java 25 toolchain.
The mod JAR is written to `build/libs`.

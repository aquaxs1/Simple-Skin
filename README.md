# Simple Skin

Simple Skin is a client-side Fabric mod for Minecraft 1.21.11. Press `K` in a
world or on a server to open a compact skin library with history, saved skins,
and the current server player list.

## Features

- Equip a stored skin immediately on the local client.
- Make the change visible to **other players**: upload to the signed-in
  Minecraft profile, wait until Mojang really serves the new texture, then
  rejoin the server so it re-broadcasts your skin to everyone online.
- Save skins from players on the current server.
- Import any 64x64 (or legacy 64x32) skin PNG from your computer.
- Keep a local history and assign one-key skin shortcuts.
- Rotate a large 3D preview, export any stored skin as PNG, and delete skins
  you no longer want.

## How other players see your skin

This is the part that a client-side mod cannot fake. Every other player's game
asks *the server* what your skin is, and the server asks Mojang's session
server exactly once — while you are logging in. So Simple Skin does the only
thing that actually reaches other people:

1. **Upload.** The PNG is pushed to your authenticated Minecraft profile.
2. **Verify.** Simple Skin polls your profile and downloads the texture Mojang
   is serving, comparing it byte for byte with what you uploaded. Mojang's CDN
   lags behind the upload by a few seconds, and rejoining too early would hand
   the server your *previous* skin.
3. **Rejoin.** Once Mojang serves the new skin, reconnecting makes the server
   re-read your profile and send the new texture to every player online.

The `Visibility` setting controls how far a change travels:

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

## Build

Use Java 21 and run `./gradlew build` (`gradlew.bat build` on Windows). The
Fabric mod JAR is written to `build/libs`.

Skin images and metadata are stored in `.minecraft/config/simple-skin`. PNG
exports are written to `.minecraft/simple-skin/exports`.

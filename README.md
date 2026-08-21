# Simple Skin

Simple Skin is a client-side Fabric mod for Minecraft 1.21.11. Press `K` in a
world or on a server to open a compact skin library with history, saved skins,
and the current server player list.

## Features

- Equip a stored skin immediately on the local client.
- Upload equipped skins to the authenticated Minecraft profile.
- Save skins from players on the current server.
- Keep a local history and assign one-key skin shortcuts.
- Rotate a large 3D preview and export any stored skin as PNG.

## Build

Use Java 21 and run `./gradlew build` (`gradlew.bat build` on Windows). The
Fabric mod JAR is written to `build/libs`.

Skin images and metadata are stored in `.minecraft/config/simple-skin`. PNG
exports are written to `.minecraft/simple-skin/exports`.

## Visibility note

The local player preview changes immediately. Simple Skin also uploads the PNG
to the signed-in Minecraft profile when an online access token is available.
Other clients may keep Mojang's cached texture until they refresh or reconnect.

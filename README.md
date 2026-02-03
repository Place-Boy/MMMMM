MMMMM (Many Minecraft Mods Made Manageable)
===========================================

NeoForge mod that keeps client mods in sync with a server-provided modpack.
The goal is to maintain server-client compatibility with a single in-game
Update button.

Key features
------------
- In-game update button on the multiplayer server list.
- modId-based sync: avoids duplicates when the .jar filename changes.
- Optional /config update (enabled by default).
- Built-in file server to host `mods.zip` and `config.zip`.

Server usage
------------
1) Start the server with the mod installed.
2) Generate the packages:
   - `/mmmmm save-mods` -> creates `MMMMM/shared-files/mods.zip`
   - `/mmmmm save-config` -> creates `MMMMM/shared-files/config.zip`
3) The embedded file server runs on the `fileServerPort` value.

Notes:
- The commands bundle *all* mods/configs at once. You can also create `mods.zip`
  and `config.zip` manually if you want to ship only specific files.

Client usage
------------
1) Open the server list and edit the target server.
2) In **Download URL**, enter the server file host (IP or URL).
   - Example: `127.0.0.1:25566` or `http://myserver:8080`
3) Return to the list and click **Update**.

Mod configuration
-----------------
Config file (COMMON): `config/mmmmm-common.toml`

- `fileServerPort` (int): file server port.
- `filterServerMods` (bool): excludes server-only mods from `mods.zip`.
- `updateConfig` (bool): updates `/config` alongside `/mods` (default: true).

How updates work
----------------
- The client downloads `mods.zip` and extracts it into `/mods`.
- For each .jar, the mod reads its `modId` and removes any older version of the same mod,
  even if the filename is different.
- If `updateConfig=true`, it also downloads `config.zip` and extracts it into `/config`.

Tips / Troubleshooting
----------------------
- If `fileServerPort` changes, the file server restarts automatically.
- If the download has no progress/ETA, the server may be missing `Content-Length`.

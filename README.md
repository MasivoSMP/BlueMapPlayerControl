# BlueMap Player Control

[![GitHub Total Downloads](https://img.shields.io/github/downloads/TechnicJelle/BlueMapPlayerControl/total?label=Downloads&color=success "Click here to download the plugin")](https://github.com/TechnicJelle/BlueMapPlayerControl/releases/latest)
[![Servers using this plugin](https://img.shields.io/bstats/servers/18378?label=Servers)](https://bstats.org/plugin/bukkit/BlueMap%20Area%20Control/18378)

Simple [Paper](https://papermc.io/) plugin that allows you to show/hide players on [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap/)

![demonstration](.github/readme_assets/demo.gif)

## Commands
| Command               | Usage                                | Permission                |
|-----------------------|--------------------------------------|---------------------------|
| `/mapaweb`                  | Sends the configured info/link message       | `bmpc`                    |
| `/mapaweb mostrar`          | Makes yourself visible                        | `bmpc.self.show`          |
| `/mapaweb ocultar`          | Makes yourself invisible                      | `bmpc.self.hide`          |
| `/mapaweb mostrar [player]` | Makes the specified player visible            | `bmpc.others.show` (OP)   |
| `/mapaweb ocultar [player]` | Makes the specified player invisible          | `bmpc.others.hide` (OP)   |

ℹ️️ Supports `@a`, `@p`, `@r` and `@s` as player arguments.

## Configuration
`config.yml` now supports:

- `default-visibility`: default visibility for players that do not yet have a saved preference (`false` by default).
- `messages.*`: all command feedback messages, parsed as MiniMessage (for example: `<red>`, `<gold>`, `<bold>`).
- `messages.base-command`: message sent when users run `/mapaweb` with no arguments.

Player visibility choices are saved in `plugins/BlueMapPlayerControl/player-visibility.db` (SQLite), so players can opt in/out and keep that preference.

## [Click here to download!](../../releases/latest)

## Support
To get support with this plugin, join the [BlueMap Discord server](https://bluecolo.red/map-discord) and ask your questions in [#3rd-party-support](https://discord.com/channels/665868367416131594/863844716047106068). You're welcome to ping me, @TechnicJelle.

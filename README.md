<div align="center">

[![Available on Modrinth](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/project/ICyROmSc)
[![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U)
[![Requires Fabric API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg)](https://modrinth.com/mod/fabric-api)

</div>
<div align="center">

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/enhanced-storage?color=00AF5C&label=downloads&logo=modrinth&style=flat-square)](https://modrinth.com/mod/skyblock-enhanced-storage)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=flat-square)](https://fluxer.gg/3jJy9cp6)

# Enhanced Storage

**A unified overlay for Hypixel SkyBlock storage — all your Ender Chests, Backpacks, and Rift Storage in one searchable view.**
</div>

## Unified Storage View

Enhanced Storage replaces the vanilla chest GUI with a custom overlay that displays all **9 Ender Chest pages**, up to **18 Backpack pages**, or your **Rift Storage** pages in a single, easy-to-navigate interface. No more memorizing which page holds what — everything is visible at a glance. (Dark Mode textures were made by [Bentcheesee](https://modrinth.com/user/Bentcheesee). Massive thanks!)

## Smart Search

Type in the search bar to filter items across **all pages simultaneously**. Whether you're hunting for a specific pet, armor piece, or crafting material, Enhanced Storage finds it in seconds without forcing you to check every page individually.

## Custom Page Names & Order

Open the edit dialog (right click on the page card) on any page card to give it a custom name and/or a custom sort position, so your most-used pages can be renamed to something memorable and pinned wherever you want them in the overview. Both are remembered per SkyBlock profile, so different profiles keep their own naming and ordering.

## Quick-Access Buttons

The overlay includes one click buttons for:
- **Settings** — jump straight into the mod's config without leaving the overlay.
- **Theme** — cycle between Transparent, Dark, and Light backgrounds on the fly.
- **Toolkit** — quickly open your Hunting or Farming Toolkit.

Each button can be individually hidden in the config if you don't use it.

## Configuration

Open the config via **Mod Menu → Enhanced Storage → Config**. Enhanced Storage uses MidnightLib, so all options are editable in-game with live saving, including overlay theme, layout (cards per row, spacing, margins), scroll behavior (speed, auto-scroll to the open page, remembering search/scroll state between visits), and cursor position saving.

## Installation

1. Install Minecraft with **Fabric Loader** for 26.1+.
2. Download the latest `.jar` from [Modrinth](https://modrinth.com/project/ICyROmSc/versions).
3. Added the **Fabric API** and **UI Lib** mod in to your `mods` folder together with Enhanced Storage. MidnightLib are bundled inside the Enhanced Storage jar — no separate downloads needed.
4. Launch the game. The overlay will activate automatically whenever you open a SkyBlock storage container.

## Support & Community

Found a bug or have a feature request? Come say hi.

[![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U)
[![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=flat-square)](https://fluxer.gg/3jJy9cp6)

## Credits & Attribution

Versions prior to **1.0.0-beta.1** adapted significant parts of the storage overlay implementation from Firmament:
https://github.com/FirmamentMC/Firmament

The following areas contained significant portions of code adapted from Firmament in those older versions:
- Storage overlay rendering
- Storage page/data handling
- Virtual inventory handling
- Related Hypixel SkyBlock storage hooks

Since 1.0.0-beta.1, the mod has been fully rewritten and no longer shares code with Firmament. This credit is kept for historical/attribution purposes.

**Textures**

Dark Mode textures were made by [Bentcheesee](https://modrinth.com/user/Bentcheesee).

> Some AI tools were used during the creation of this project.

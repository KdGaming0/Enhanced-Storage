<div align="center" style="text-align: center">

[![Available on Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/mod/skyblock-enhanced-storage) [![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U) [![Requires Fabric API](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg)](https://modrinth.com/mod/fabric-api)

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/ICyROmSc?color=00AF5C&label=downloads&logo=modrinth&style=flat-square)](https://modrinth.com/mod/skyblock-enhanced-storage) [![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=flat-square)](https://fluxer.gg/3jJy9cp6)

# Enhanced Storage

**All your Hypixel SkyBlock storage in one overlay: searchable, highly customizable, and lightning fast.**

</div>

<div align="center" style="text-align: center">

<img src="https://cdn.modrinth.com/data/ICyROmSc/images/fb7515cc91d017736363e37548c86dbb45a95de0.webp" alt="Enhanced Storage overlay cycling through layouts: default, Dense, Nav Bar on either side, inventory moved to the side, and Edit Mode">

</div>

## Features

### Page Caching

Since the mod is client-side only, it can only see the page you currently have open. When you close the storage menu or navigate to a different page, the mod saves a snapshot of the last-seen contents and displays it as a cached page. You cannot pick up items from or move items into cached pages; you have to open them first. However, you can hover over items to see what they are.

### Edit Mode

<img src="https://cdn.modrinth.com/data/ICyROmSc/images/e0e549854f5da19e1fa5bf2c19925dbf0158110d.webp" alt="Reordering pages by dragging them, then stacking two pages by dropping one on another page's edge" width="400" align="right" style="float: right; margin-left: 16px">

Edit Mode lets you organize the overlay however you like. To enter Edit Mode, click the button with the wrench icon beside the inventory.

While in Edit Mode, you have the following options:

- **Rename Pages:** Click the title of any page and start typing to give the page a custom name for better organization. Names support Minecraft [formatting codes](https://minecraft.wiki/w/Formatting_codes) for colors and styles; just type `&` instead of `§`. For example, `&cWeapons` shows "Weapons" in red, and `&l` makes text bold.
  - Press Enter or click outside the title to save, press Escape to cancel, and clear the name to reset it.
- **Reorder Pages:** Click and drag any page to reorder it. Dropping on the left half of a page puts it before that page, and the right half puts it after.
- **Custom Page Widths:** Right-click any page and choose how wide it should be, from 2 to 18 slots.
- **Stacked Pages:** Drag a page onto the top or bottom edge of another page, and they'll stack together in one column. The pages around a stack treat it as a single page, so you can, for example, place one tall page beside two wide pages stacked on top of each other. Pages in a stack always share the same width, and you can still drag each page on its own. To take a page out of a stack, drag it somewhere else, or right-click it and choose **Remove from Stack**.
- **Spacers:** Spacers are empty, invisible pages that you can place between existing pages. To add a spacer, right-click any page. You'll see an option to add a spacer before or after it. Once you've added a spacer, you can change its width or tell it to use the rest of the row. For example, if you add a spacer after page 1 and make it use the rest of the row, no more pages will appear in that row.
- **Sections:** Hover your mouse between two rows of pages, and the text "Add new section here" will appear. Click it to create a section. Once you have created a section, you can drag pages into it. Sections are perfect for grouping pages by activity, such as keeping all your farming-related stuff in one section and mining stuff in another. Sections can also be collapsed to hide pages and save space if you don't need to see their contents often, such as when you have a few pages with investments.
- **Undo and Redo:** Press <kbd>Ctrl</kbd> + <kbd>Z</kbd> to undo your last change and <kbd>Ctrl</kbd> + <kbd>Y</kbd> (or <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>Z</kbd>) to redo it. This works for moving, stacking, renaming, page widths, spacers and sections. After you reset or import a layout, an **Undo** button appears in the Edit Mode panel so you can get your old layout back with one click.
- **Share Your Layout:** Click the **Share** button in Edit Mode and choose **Copy Layout** to copy your layout to the clipboard as text. Send it to a friend, or save it somewhere safe as a backup, for example when you move to a new computer. To use a layout, copy the whole text, click **Share**, and choose **Paste Layout**. You can choose what to bring over: the page arrangement, page widths, and page names. Pages the other player doesn't have are skipped, and pages that aren't in the shared layout keep their usual spot.

### Nav Bar / Storage Overview

Beside the inventory, there is a Storage Overview widget. There, you can see all the Ender Chest and backpack slots, as you do when you first open storage in vanilla. When the Storage Overview title is gold, it is the menu you currently have open, so you can interact with its slots directly. Click any of the items to open that page, or right-click them to pick up backpacks or place new backpacks into your storage. (The Storage Overview widget can be disabled.)

<img src="https://cdn.modrinth.com/data/ICyROmSc/images/dc5036397a12ed1ea4f0d16a8f83781fed0aca1c.webp" alt="Enhanced Storage overlay showing Hypixel SkyBlock storage pages grouped into Combat, Mining and Farming sections with colored custom names, a Nav Bar listing every page on the right, and the inventory moved beside the pages." width="400" align="right" style="float: right; margin-left: 16px">

You can optionally enable a Nav Bar that adds a widget to the left or right of the storage overlay, listing all your pages in order. From there, you can click the page you want to open. Used together with custom names, this can greatly increase navigation speed.

### Inventory

The inventory can be moved to the left or right of the storage overlay to give the pages more room, letting you see even more pages and items at once. It works great with the Nav Bar when the Storage Overview is disabled.

### Search

<img src="https://cdn.modrinth.com/data/ICyROmSc/images/d5553d26ca989ed79c8e5d39516ce6ff461f22de.webp" alt="Typing a search query and the overlay filtering down to only the pages with matching items" width="400" align="right" style="float: right; margin-left: 16px">

At the top of the inventory, you will see a search bar. Type into it to find items quickly. You can also use `/ecs <query>` to open the storage overlay with a search already filled in.

When searching, pages without matches are hidden by default. In the settings, you can switch to **Dim** mode instead, which keeps every page in place and dims the items that don't match.

More search options:

- **Search Bar Autofocus:** Start typing while the overlay is open without having to click the search bar first.
- **Search Page and Section Names:** Find pages and sections by their custom names.
- **Search-fill keybind:** Fills the search bar with the name of the item under your cursor. It has no default key, so set one in Controls.
- **Colors:** The search highlight color and opacity can be changed.

### Highlight Matching Items

<img src="https://cdn.modrinth.com/data/ICyROmSc/images/4504421d226e356580989433244822c59f10db92.webp" alt="Holding Left Alt over a pickaxe highlights every copy across the pages, with a 35× total next to the cursor" width="400" align="right" style="float: right; margin-left: 16px">

While you hold Left Alt (you can change the key in Controls), all items of the same type are highlighted. When an item is highlighted, a small tooltip shows how many of that item you have, and pages without a match are hidden. Both options can be turned off. Together, these features let you quickly see how many of an item you have and which pages it is on.

In the settings, you can also choose to always highlight matching items when you hover over one, without holding the key, and change the highlight color and opacity.

### Keyboard Navigation

While the overlay is open, you can quickly change the open page using the arrow keys. Up opens the page above the active page, and down opens the page below it. Left and right open the pages to the left and right. You can also enable an option to navigate with WASD.

You can also press Shift + 1–9 to quickly open one of the first nine pages.

### Compact Layout

Fit more on the screen:

- **Compact Name** makes the page a bit shorter and shows the name in the smallest space needed. The name will overlap the edge of the page. You can also turn off the name entirely.
- **Slot Size** can be set to **Compact**, which reduces the slot size from 18px to 16px. You can see more items at once, but the items will overlap the slot edges.
- **Layout Density** lets you quickly change how much padding the menu uses around its items. You can choose **Normal**, **Compact**, or **Dense**. Normal is the default. Compact moves everything closer together, while Dense moves everything even closer and uses less padding, so more pages are visible at once. A **Custom** option lets you fine-tune all the values yourself.

### Other

- By default, a small number appears in the top right of each page to show how many slots are free. You can turn this off. When a page is full, the word "Full" appears in red, so it's easy to see which pages are full.
- **Overlay GUI Scale:** A separate GUI scale for the overlay. 0 follows your normal GUI scale. It also scales other mods drawing on the same screen.
- **Invert Scroll Direction** for the pages and the Nav Bar.
- **Hide Enchant Glint on Cached Pages.**
- **Center the whole overlay**, rather than just the storage part. This is useful when the inventory is on the side.

## Keybinds and Commands

| Action                        | Key / Command                                                                                                          |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Highlight matching items      | <kbd>Left Alt</kbd> (rebindable)                                                                                       |
| Move between pages            | <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> (or <kbd>W</kbd> <kbd>A</kbd> <kbd>S</kbd> <kbd>D</kbd>, optional) |
| Open page 1–9                 | <kbd>Shift</kbd> + <kbd>1</kbd>–<kbd>9</kbd>                                                                           |
| Fill search with hovered item | Unbound by default (set in Controls)                                                                                   |
| Open storage (key)            | Unbound by default (set in Controls)                                                                                   |
| Undo / redo in Edit Mode      | <kbd>Ctrl</kbd> + <kbd>Z</kbd> / <kbd>Ctrl</kbd> + <kbd>Y</kbd>                                                         |
| Open storage                  | `/ecs`                                                                                                                 |
| Open storage with a search    | `/ecs <query>` or `/enhancedstorage ecs <query>`                                                                       |
| Open the config               | `/es config` or `/enhancedstorage config`                                                                              |
| Copy your layout as text      | `/es layout export` (add `rift` for Rift Storage)                                                                      |
| Paste a layout                | `/es layout import` (add `rift` for Rift Storage)                                                                      |

## Performance

Enhanced Storage was heavily optimized for maximum FPS in version 2.0. Normally, when you open a large menu with hundreds of items, the FPS drops significantly. With Enhanced Storage, the FPS stays stable after you open the overlay, so you can expect almost the same FPS in the overlay as when your inventory is open.

**Enhanced Storage 1.x vs. 2.0**

| Overlay open               | Avg FPS               | 1% low        | 0.1% low      |
| -------------------------- | --------------------- | ------------- | ------------- |
| **Desktop**, test instance | 282 → **1415** (5.0×) | 152 → **370** | 125 → **329** |
| **Desktop**, modpack       | 157 → **468** (3.0×)  | 82 → **142**  | 61 → **105**  |
| **Laptop**, test instance  | 238 → **580** (2.4×)  | 130 → **164** | 118 → **125** |
| **Laptop**, modpack        | 72 → **128** (1.8×)   | 45 → **68**   | 35 → **50**   |

<sub>Desktop: Ryzen 7 5700X3D, RX 9070 XT. Laptop: Ryzen 7 8840HS, Radeon 780M. Tested on Hypixel SkyBlock while looking at the void. Test instance: Sodium, ImmediatelyFast and Gnetum. Modpack: Skyblock Enhanced v5.2.3.</sub>

Two settings help with this. **Enchant Glint Refresh Rate** (default: 30 FPS) updates the enchant shimmer less often for a big FPS gain; set it to **Smooth** for the old behavior. **Cache Page Rendering** (default: on) draws unchanged pages once and reuses them. Turn it off if a page looks out of date or broken.
## Compatibility

The mod should work with most, if not all, other mods. If you find an issue, please let me know.

Compatibility has been added for Skyblocker's Quick Nav (the storage overlay resizes to fit the Quick Nav buttons) and for RRV.

## Installation

To use Enhanced Storage, you need the [Fabric Loader](https://fabricmc.net/use/installer/), [Fabric API](https://modrinth.com/mod/fabric-api), [UI Lib](https://modrinth.com/mod/ui-lib), and a supported version of Minecraft. You can see the supported versions on the [versions page](https://modrinth.com/mod/skyblock-enhanced-storage/versions).

After installing Fabric, place Fabric API, UI Lib, and the [latest version of Enhanced Storage](https://modrinth.com/mod/skyblock-enhanced-storage/versions) in your mods folder.

If you want a complete modpack for Hypixel SkyBlock, you can use my [SkyBlock Enhanced modpack](https://modrinth.com/modpack/skyblock-enhanced). It includes this mod and many other SkyBlock mods, giving you everything you may need in one download.

## FAQ

**Is this allowed on Hypixel?**

Enhanced Storage is client-side and only changes how the storage menu is displayed, using data the server already sent. It doesn’t automate anything or give an unfair advantage. As with any mod, use it at your own discretion.

**How do I open the settings?**

Use `/es config`.

## Support & Community

Found a bug or have a feature request? Come say hi.

[![Chat with us on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/FCPP2WPZ3U) [![Join Fluxer](https://img.shields.io/badge/Join-Fluxer-5865F2?style=flat-square)](https://fluxer.gg/3jJy9cp6)

## Credits & Attribution

Versions prior to **1.0.0-beta.1** adapted significant parts of the storage overlay implementation from [Firmament](https://github.com/FirmamentMC/Firmament).

The following areas contained significant portions of code adapted from Firmament in those older versions:

- Storage overlay rendering
- Storage page/data handling
- Virtual inventory handling
- Related Hypixel SkyBlock storage hooks

Since 1.0.0-beta.1, the mod has been fully rewritten and no longer shares code with Firmament. This credit is kept for historical and attribution purposes.

**Textures**

Dark Mode textures were made by [Bentcheesee](https://modrinth.com/user/Bentcheesee).

**Libraries**

Built with [UI Lib](https://modrinth.com/mod/ui-lib) by daqem.
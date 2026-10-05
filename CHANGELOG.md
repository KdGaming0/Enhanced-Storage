## Update 2.0.0

### New Features and Improvements

#### Edit Mode
Edit Mode lets you make the storage overlay your own. Open it with the new **Edit** button beside your inventory (you can hide the button in the settings). From there, you can:
- **Rename pages:** Click a page's title and type a new name. Press Enter or click outside the title to save, or press Escape to cancel. Clearing the name restores the default name.
  - Page and section names can be colored with `&` color codes, like Minecraft's chat codes. For example, `&cWeapons` shows "Weapons" in red and `&l` makes text bold. You can find all the color codes here: https://minecraft.wiki/w/Formatting_codes.
- **Reorder pages:** Click and drag any page to move it.
- **Create sections:** Click anywhere between two rows of pages to show the new **Add Section** text. Click it to create a section. You can group related pages in sections and hide them when you don't need them.
  - You can give sections custom names and expand or collapse them. This is useful for pages you don't always need to access quickly. A collapsed section appears as a single bar showing its name and the total number of free slots on its pages.
- New settings:
  - **Custom Page Width:** Set how many slots wide pages are by default. Instead of the default 9 slots, you can choose any number from 2 to 18. You can also change the width of individual pages in Edit Mode.
  - **Hide Item Tooltips in Edit Mode** stops tooltips from covering pages while you arrange them.
  - **Collapse Sections While Dragging** shows every section as a collapsed bar while you move a section.

#### Custom Page Layouts
A new page layout system gives you more control over how your pages are arranged.
- **Custom page widths:** Each page can be 2 to 18 slots wide. Right-click a page and choose **Page Width...** to set its width.
- **Spacers:** Right-click a page and choose **Add Spacer Before** or **Add Spacer After**. A spacer leaves empty space where a page would normally be, making it easy to separate groups of pages.
- **Row breaks:** Right-click a page and choose **Add Row Break After** to start a new row after it. In Edit Mode, a break appears as a faint "Row break" area. Right-click the area or the page to remove it. Unlike a spacer, which takes up a fixed amount of space, a row break moves the next page to a new row.
- **Stacked pages:** You can drag a page onto the top or bottom edge of another page to stack them. Drag it away again to unstack it. This is useful if you want one tall, thin page beside two wide, short pages.
- **The overlay's width now adapts to your pages:**
  - **Max pages per row** is now measured in pages that are 9 slots wide, so more narrow pages can fit in a row. For example, if you set the maximum to 3, you can fit 6 pages in a row if they are all 4 slots wide. Each page in the setting allows 9 slots of overlay width, and the number of pages that fit depends on their widths.
  - The overlay shrinks to fit when there are only a few pages, but it won't grow wider than the screen.

#### Navigation Panel
A new panel on the side of the overlay makes it easier to navigate your pages and sections. The panel:
- Lists all your pages, grouped by section. Click a page to open it, or click a section to scroll to it.
- Can be placed on the left or right of the overlay, or turned off in the settings. (This feature is by default off and needs to be enabled in settings.)

#### Highlight Matching Items
- Hold Left Alt while hovering over an item to highlight every stack of that item across all pages and your inventory.
  - You can change the key in the controls. In the settings, you can also choose to always highlight matching items when you hover over one.
- A new **Hide Pages Without Copies** setting has been added. When it's on, holding the highlight key shows only pages containing the item you're hovering over. This gives you a quick view of how many of that item you have and which pages contain it.
- A new **Show Item Totals** setting (on by default) shows how many of the highlighted item you have across your pages and inventory, like `46×`. It appears as a small tooltip next to your cursor.

#### Search Improvements
- New **Dim** search mode. Instead of hiding pages without matching items, it keeps every page in place and dims items that don't match.
  - The old **Hide** mode is still available and remains the default. You can switch to Dim mode in the settings.
- A new **Search Page and Section Names** setting has been added. When it's on, you can search for pages and sections by their custom names.
- A new keybind fills the search bar with the name of the item under your mouse cursor. It has no key set by default, but you can set one in the vanilla Controls settings.
- New **Search Bar Autofocus** setting. When it's on, you can start typing anywhere to search, without clicking the search bar first. While your mouse is over an item, keys work as usual, so you can still swap items with 1–9 or drop them with Q.
- New color and opacity settings for the search highlight and for **Highlight Matching Items**, so you can pick colors that are easier for you to see.

#### Smaller Improvements
- Added an **Invert Scroll Direction** setting for the pages and the navigation panel.
- Added a **Hide Enchant Glint on Cached Pages** setting that removes the enchant shimmer from items on pages that aren't open.
- Added keyboard navigation. Use the arrow keys to move to the page above, below, left, or right of the current one. Use Shift + 1–9 to open one of the first nine pages.
- Each page now shows its number of free slots in the top right corner of its title bar. You can turn this off in the settings.
- Added the ability to move the Inventory to the left or right of the overlay. 
  - This also comes with the new config option to center the whole overlay on the screen instead of just the storage part. Useful when the inventory is on the side, so the overlay utilizes the whole screen better.
- Added a new setting to make page titles compact or hide them. When set to compact, the title bar is only tall enough to show the title. When hidden, the title bar is gone.
- Added a new setting to quickly change how compact the overlay is. The new **Layout Density** setting has four options: **Custom**, **Normal**, **Compact**, and **Dense**. It changes the spacing between pages and setting it to compact or dense can let you see more pages at once. Custom lets you use the existing sliders to fine-tune the values your self.
- Added a **Slot Size** setting. **Compact** uses smaller slots with no gap between items, so pages take up less space and more of them fit on screen. The Storage Overview and your inventory keep normal slots. The **Dense** layout uses compact slots, and you can change the setting afterwards.
- Added a **Room for Other Mods** setting. When set to **Automatic** (the default), the overlay leaves just enough space for Skyblocker's Quick Nav tabs, RRV's search bar and SkyRecipes' category buttons — only for the ones you have turned on — so the overlay can use more of your screen. **Manual** lets you set the space yourself, like before.
- Skyblocker's Quick Nav tabs now line up with your storage pages and inventory instead of the middle of the screen.

#### Performance
**Storage Overlay is now 2–5× faster**, especially with a large window and many pages on screen. It now costs about 3 ms less per frame, so slower PCs get a smaller multiplier but the same time saved.

| Overlay open | Avg FPS | 1% low | 0.1% low |
|---|---|---|---|
| **Desktop** (Ryzen 7 5700X3D, RX 9070 XT) | 264 → **1436** (5.4×) | 172 → **310** | 156 → **245** |
| **Laptop** (Ryzen 7 8840HS, Radeon 780M) | 170 → **350** (2.1×) | 78 → **115** | 55 → **80** |
<sub>Tested on Hypixel SkyBlock while looking at the void, with Sodium, ImmediatelyFast and Gnetum installed.</sub>

**New settings**
- **Enchant Glint Refresh Rate** (default: 30 FPS): Updates the enchant shimmer less often while the overlay is open. The shimmer looks slightly less smooth, but you get a lot more FPS. Set it to **Smooth** for the old behavior.
- **Cache Page Rendering** (default: on): Pages that aren't changing are drawn once and reused instead of being redrawn every frame. Turn it off if a page looks out of date or another mod's item overlays stop updating.

### Fixes
- Fixed an issue that stopped the storage cache from being saved between game restarts for some players.
  - Hypixel doesn't automatically send your profile ID when you have only one SkyBlock profile. The mod now requests it with `/profileid` after you join SkyBlock, so the cache can be saved correctly.
- Fixed items in an open storage page briefly appearing in the top row of the overlay before moving to the correct page.
- Fixed a brief flash that sometimes appeared when switching between storage pages.
- Fixed items in RRV's item list being invisible while the storage overlay is open with RRV's Recipe Book theme.

## Update 1.2.2

- Reduced unnecessary work while the storage overlay is open by refreshing cached page contents only after actual container updates.
- Improved overlay performance when viewing and scrolling through many cached storage pages.
- Made storage cache saves more reliable, especially when several pages are updated in quick succession.

## Update 1.2.1

- Made `/ecs` work without a query, and now opens the storage as normal.

## Update 1.2.0

- Added client commands for opening SkyBlock Storage with a search query already filled in.
  - `/ecs <query>` or `/enhancedstorage ecs <query>` opens the storage overlay with the search bar filled with `<query>`.
- Added commands to open the config menu.
  - `/es config` or `/enhancedstorage config`.

## Update 1.1.1

- Fixed the game crashing on startup when RRV 8.7.0 or newer was installed alongside Enhanced Storage.
- Fixed all items and the tooltip turning invisible when hovering a slot in the storage overlay with Smooth Hover installed.

## Update 1.1.0

- Added an "Overlay GUI Scale" setting, letting the storage overlay use its own GUI scale without changing the rest of the game. Leave it at 0 to keep following your normal GUI scale. A scale too large for your window is reduced to the biggest that still fits.
  - When set to anything other than 0, this changes the GUI scale Minecraft itself uses while the overlay is open. That means other mods drawing onto the same screen are scaled with it.
- Fixed the search bar dropping characters when typing fast.

## Update 1.0.1

- Fixed the search bar briefly freezing and not accepting inputs while typing fast. Searching is now much faster and no keystrokes get lost.
- Fixed search not finding the Personal Compactors in the storage overlay when SkyHanni's "Personal Compactor Overlay" feature is enabled.

## Update 2.0.0

## New Features and Improvements
- Added a new "Customize Storage" mode, from here you can now edit the storage overlay's appearance and behavior to your liking.
  - Pages can now be renamed in Customize Storage mode: click a page's title and type a new name. Press Enter or click anywhere outside the title to save, or press Escape to cancel. Clearing the name restores the default.
  - Pages can now be reordered in Customize Storage mode: click and drag a page to move it. The new order is saved automatically.
  - Added a "Spacer" page type, which can be used to create empty space between other pages. While in Customize Storage mode, right-click any page to add a "Spacer" to the left or right of it. Spacers are empty spaces where a page would normally be. They can be used to create sections and separate pages, giving you more control over the overlay's layout.
  - Added a new "Section" feature. Create new sections anywhere in the overlay between two rows while in Customize Storage mode. Sections can be named and collapsed, and they can contain any number of pages. This allows you to group related pages together and hide them when not needed.
  - Added a new "Navigation" panel. Can be Disabled or Placed LEFT/RIGHT of the overlay. It shows all pages and sections in a list, allowing you to quickly jump to any page or section.
  - Each page now shows how many free slots it has left in the top right corner of its title bar. Can be disabled in the settings.
  - Added a new Search mode "DIM". While in Dim mode pages don't get hidden when they have no matching items, instead they are dimmed.
  - Added the ability to highlight matching items in the storage overlay. By default, while holding Left Alt, all slots containing the same item are highlighted in blue. You can change this in the settings to use a different key or to always highlight matching items when hovering over them. You can also enable a setting to show only pages with matching items while holding Left Alt.
  - Added keyboard navigation for the storage overlay. You can now use the arrow keys to move between pages, Up selects the page over the current one, Down selects the page under it, Left selects the page to the left, and Right selects the page to the right. Shift + 1–9 opens the 9 first pages.

## Custom Page Layouts

### New
- **Custom page widths:** each page can be 2 to 18 slots wide. Right-click a page in edit mode and choose **Page Width...** to set it with a slider, or use **Reset to Default**.
- **Global Page Width setting** now goes up to 18 slots.
- **Automatic stacking:** when a page is shorter than the row it's in, the pages after it fill the free space beside the tall page in lines, instead of leaving it empty.
- **Manual stacks:** drag a page onto the top or bottom edge of another page to stack them. A stack moves and flows as a single card. Drag a page out of a stack to unstack it.
- **Row breaks:** right-click a page and choose **Add Row Break After** to start a new row after it. Breaks show as a faint "Row break" area in edit mode; right-click it or the page to remove it.

#### Changed
- **Overlay width adapts to your pages:**
  - **Max pages per row** is now measured in default-width pages, so narrow pages fit more per row.
  - The overlay shrinks to fit when there are only a few pages.
  - The navigation panel narrows the overlay only as much as it needs, instead of removing a whole column.
- **Pages never draw wider than they need:** a page is limited to its own slot count and the available screen width. Your saved width is kept.
- **Smoother dragging:**
  - Dropping on the left half of a page places the dragged page before it, and the right half places it after.
  - Pages no longer flicker back and forth when dragging between cards of different sizes.
- Spacers and row breaks are placed beside a stack instead of splitting it.
- While a search is filtering pages, spacers, row breaks and stacks are ignored so results flow freely. They come back when the search is cleared.

#### Notes
- Existing layouts load unchanged. Layouts with only default-width pages look the same as before, except that the overlay is narrower when a group has fewer pages than "Max pages per row".
- Some rows with mixed page heights may arrange differently because of automatic stacking. Use manual stacks or row breaks to adjust them.

### Fixes
- Fixed Storage Overlay cache not saving for some players between game restarts.
  - When a player has only one SkyBlock profile, Hypixel does not automatically send the Profile ID chat message. The mod now requests it with `/profileid` after entering SkyBlock, so it can save the cache correctly.
- Fixed items in an opened storage page briefly appearing in the top row of the overlay before moving to the correct page.
- Fixed a brief flash that sometimes appeared when switching between storage pages.

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

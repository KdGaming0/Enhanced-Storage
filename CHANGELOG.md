## Update 2.0.0

## New Features and Improvements
- Added a new "Customize Storage" mode, from here you can now edit the storage overlay's appearance and behavior to your liking.
  - Pages can now be renamed in Customize Storage mode: click a page's title and type a new name. Press Enter or click anywhere outside the title to save, or press Escape to cancel. Clearing the name restores the default.
  - Pages can now be reordered in Customize Storage mode: click and drag a page to move it. The new order is saved automatically.
  - Added a "Spacer" page type, which can be used to create empty space between other pages. While in Customize Storage mode, right-click any page to add a "Spacer" to the left or right of it. Spacers are empty spaces where a page would normally be. They can be used to create sections and separate pages, giving you more control over the overlay's layout.
  - Added a new "Section" feature. Create new sections anywhere in the overlay between two rows while in Customize Storage mode. Sections can be named and collapsed, and they can contain any number of pages. This allows you to group related pages together and hide them when not needed.
  - Added a new "Navigation" panel. Can be Disabled or Placed LEFT/RIGHT of the overlay. It shows all pages and sections in a list, allowing you to quickly jump to any page or section.

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

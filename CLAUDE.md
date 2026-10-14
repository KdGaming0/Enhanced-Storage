# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Project overview

Enhanced Storage is a Fabric client mod for Hypixel SkyBlock (Minecraft) that replaces the vanilla chest GUI with a unified, searchable overlay showing all Ender Chest, Backpack and Rift Storage pages at once.

## Build system

Stonecutter (multi-Minecraft-version Gradle) generates per-version project copies, so tasks can be prefixed with a version — `./gradlew :26.1:runClient` launches a dev client for that version, against a `run/` directory shared across versions. Versions are declared in `settings.gradle.kts` (currently `26.1`, `26.2` and `26.3`); `26.1` is the VCS/active version, so the checked-in source is the 26.1 variant.

- `./gradlew buildAndCollect` — build and copy the mod jar (+ sources jar) into `build/libs/<mod.version>/`.
- `./gradlew chiseledSrc` / stonecutter migrate tasks — used only when adding/porting to a new Minecraft version; don't invoke unless asked.

There is no separate lint or test task configured — `build` is the correctness gate (compiles, applies mixins, generates resources).

Version/mod metadata (mod id, version, dependency versions per MC version) is centralized in `stonecutter.properties.toml`, not scattered across `build.gradle.kts`. Bump versions there.

## Architecture

Client-only Fabric mod, single entrypoint: `EnhancedStorage` (`ClientModInitializer`).

Use `graphify query` to orient in the module graph. Non-obvious contracts:
- **Per-profile persistence**: everything saved per SkyBlock profile (`PageCache`, `PageNames`, `PageOrder`, `PageLayout`, `PageWidths`) implements `ProfileScopedStore` and must be registered in `EnhancedStorage.onInitializeClient` via `ProfileSession.register(...)`. A new store that isn't registered is never loaded or saved.
- **Page layer cache**: `PageViewport` draws its cells once into an offscreen layer (`PageLayerCache`) and redraws it only when its rows, scroll, rect, live-cell set or owner inputs change, plus a 1 s safety refresh. The hovered cell is drawn live into a gap cut out of the layer, so a cell must not draw outside its own bounds (that part shows the cached, unhovered version). A `PartialHoverCell` (page cards) narrows the gap to its `hoverArea` (the hovered slot's highlight), which must hold everything hovering changes; non-drawn feedback like the cursor goes in `requestHoverCursor`. Anything a cell draws that can change without a reflow must be either a `setPageLayer` input, a `LiveCell` (drawn every frame), or drawn outside the viewport (like `StorageOverlay.paintEditLayer`). Otherwise it stays stale for up to 1 s. Animated items (glint, animated textures) and player heads still loading their skin are kept out of the layer by `LayerItems.defer` and drawn into a second cache (`PageLayerCache.ANIMATED`) at the glint rate, or live on Smooth; anything a cell draws over such an item must be deferred with it. The Storage Overview panel is a `CachedPanelComponent` using the same layer (redraw timing in `LayerRefresh`); hovering may only show its items' tooltips, and owners must not overlap since they share one target.
- **Edit-mode undo and layout sharing** both work on `LayoutSnapshot` (arrangement + widths + names of one context). Per-context layout data added later must be added to `LayoutSnapshot`, `StorageOverlayLayout.snapshot`/`applySnapshot` and `LayoutTransfer`, or undo and sharing silently skip it.
- **GUI scale**: our screens may be drawn at a scale other than the window's. Overlay GUI Scale switches the window while open (`OverlayScale`), but Better Screens' container scale switches it only around the screen's extract and its own second `GuiRenderer`'s render. Take sizes from the screen's `width`/`height`, not the window, in anything that also runs from ticks or input; and anything tied to "the GUI renderer" must handle more than one per frame (see `PageLayerCache.renderPending`, `AnimatedItemThrottle.endFrame`).
- **Keybinds** share `CopyHighlight.CATEGORY`; `KeyMapping.Category.register` throws if the same id is registered twice.
- **Profile switch**: `ProfileSession` owns profile state (`UNKNOWN` → `GUESSED` from `last_profile.txt` on join → `CONFIRMED`). On a switch, stores are **reloaded** from the new profile dir (not just re-read) and `StorageEvents.PROFILE_CHANGED` fires; listeners rebuild UI. Saves are held back until the profile is `CONFIRMED`, pages captured before then are staged, and `requestSave()` debounces writes (~10s).

# Mixins

Mixins live in `mixin/` and are declared in `src/main/resources/enhancedstorage.mixins.json`. Third-party compat mixins live under `mixin/compat/{betterscreens,rrv,skyblocker,skyhanni}/` and `EnhancedStorageMixinPlugin.shouldApplyMixin` gates each package behind `FabricLoader.isModLoaded(...)` — they must stay optional at the mixin-plugin level, not just at runtime, since those mods are compile-only deps (see `modCompileOnly` entries in `build.gradle.kts`).

When adding a compat mixin, also add its injection-target method names to `REQUIRED_TARGET_METHODS` in the plugin: it checks the target's bytecode and skips the mixin if the method was renamed/removed, so an update to the other mod disables the feature instead of crashing the game. Prefer the argument-less handler form (`CallbackInfo` only) so signature changes don't break APPLY. MixinExtras `@Expression` works only because the mixin config declares `mixinextras.minVersion`; `require = 0` doesn't stop a setup error from crashing at launch.


## Verifying APIs

Your training data may predate MC 26.x and the current versions of the compat mods. Don't rely on memory for Minecraft, Fabric or third-party class names, signatures, mapping names or injection targets. Verify them against the actual bytecode or source (`javap` on the Loom Minecraft jar or the dependency jar in `~/.gradle/caches`). For compat work, check the version I actually run, not just the `modCompileOnly` pin.

Nullability annotations are jspecify (`org.jspecify.annotations.Nullable`/`NonNull`), not JetBrains. They're TYPE_USE-only, so a nested type is written `Outer.@Nullable Inner`.

Minecraft 26.3 replaces GLFW with SDL. Use `InputConstants` for keys, mouse buttons and modifiers, not `org.lwjgl.glfw.GLFW`, and don't do arithmetic or range checks on key codes (SDL's differ); use explicit sets or tables. Never compare mouse buttons with literals: left is 0 before 26.3 and 1 from 26.3 on, so use `InputConstants.MOUSE_BUTTON_LEFT`/`RIGHT`. Text inputs must report focus changes through `Minecraft.onTextInputFocusChange` (vanilla `EditBox.setFocused` already does).

# Workflow

Investigate → plan → get approval → implement → validate → document. Follow the `work-on-problem` skill; this file adds only the project specifics.

## When to keep going and when to stop

- **Before approval**: stop after the plan and wait. No code before I approve.
- **After approval**: when a step doesn't need my input, keep going. Put status notes in the same message as your next action. Don't stop just to report progress or offer to continue.
- **Stop and ask** when you can't continue without me, when the approved plan turns out to be wrong or would change existing behavior, or before anything destructive: deleting files or data outside the plan, any `git push` or history rewrite, or writing anywhere outside this repository. Reading outside the repo (e.g. launcher profiles to find the mod jars I run) is fine.

For work with many steps, keep a checklist in `TASKS.md`, tick items as you finish them, and add anything new you find. Delete it once the task is documented.

## Validate

Run `./gradlew build` before considering the work complete and report the real result. It is the only automated gate. Where logic is MC-free, verify the shipped code standalone (see the skill's gotchas for the JDK 25 and classpath setup).

Most behavior can only be verified in game. When it is, describe what to test, how to reproduce it, and the expected result, then wait for me to report back before documenting.

## Reporting

End every run with these headings, omitting any that are empty:

- **Needs from you**: decisions, approvals, in-game tests to run
- **Changed**: each file touched and what the change does, in plain language
- **Found**: findings, surprises, and anything you couldn't confirm (say where you looked)

## Documentation

After testing has passed:

- Add a concise entry to `IMPLEMENTATION_LOG.md`: what changed, why, and notable decisions. The whole file stays under 500 lines.
- If the work fixes a bug or adds a user-visible feature, add a short, non-technical entry to `CHANGELOG.md` for end users, with no implementation details or internal terms.
- Update this file only if a convention or contract changed.

## Principles

- **Scope**: only modify files needed for the requested change. No unrelated formatting, refactoring or reorganization unless asked or required to complete the task safely.
- **Preserve behavior**: unless the request explicitly changes it, keep existing behavior. If a change or trade-off is needed, raise it in the plan and wait for approval.
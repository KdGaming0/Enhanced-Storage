# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Project overview

Enhanced Storage is a Fabric client mod for Hypixel SkyBlock (Minecraft) that replaces the vanilla chest GUI with a unified, searchable overlay showing all Ender Chest and Backpack pages at once.
## Build system

This project uses **Stonecutter** (multi-Minecraft-version Gradle) on top of **Fabric Loom**. Source lives once under `src/`, and Stonecutter generates per-version project copies under `versions/<mc_version>/` at build/sync time — currently only `26.1` is active (see `stonecutter.gradle.kts` / `settings.gradle.kts`).

Common commands (run from repo root):
- `./gradlew build` — build the active version (what CI runs).
- `./gradlew :26.1:build` — build a specific Stonecutter version explicitly.
- `./gradlew :26.1:runClient` — launch a dev client for that version (shared `run/` directory across versions, configured in `build.gradle.kts`).
- `./gradlew buildAndCollect` — build and copy the mod jar (+ sources jar) into `build/libs/<mod.version>/`.
- `./gradlew chiseledSrc` / stonecutter migrate tasks — used only when adding/porting to a new Minecraft version; don't invoke unless asked.

There is no separate lint or test task configured — `build` is the correctness gate (compiles, applies mixins, generates resources).

Version/mod metadata (mod id, version, dependency versions per MC version) is centralized in `stonecutter.properties.toml`, not scattered across `build.gradle.kts`. Bump versions there.

## Architecture

Client-only Fabric mod, single entrypoint: `EnhancedStorage` (`ClientModInitializer`). On init it wires up MidnightLib config, registers a profile-change listener that reloads storage caches/names and rebuilds any open overlay, and registers the `StorageCaptureHandler`.

The god nodes from the dependency graph mark the real center of gravity — start here when orienting:
- `StorageKey` (`storage/StorageKey.java`) — identifies a single storage page (Ender Chest N, Backpack N, Rift N, or the index screen) by parsing vanilla screen titles / item names via regex. Nearly every other module keys off this record.
- `StorageOverlayLayout` / `StorageOverlay` / `StorageOverlayState` (`gui/`) — build and render the actual overlay UI (page cards, slots, search) on top of the vanilla container screen.
- `StorageContainerScreen` (`screen/`) — the screen-level integration point; bridges vanilla `AbstractContainerScreen` behavior with the overlay and exposes `rebuildForProfileChange()`.
- `StorageCache`, `StorageProfile`, `StorageNames`, `StorageOrder` (`storage/`) — persistence: cached page contents, per-profile (per-account/per-world) storage, custom names/ordering. `StorageProfile.setOnChange` is the hook point for "player switched SkyBlock profile" — caches must be reloaded, not just re-read, on that event.
- `StorageCaptureHandler` (`storage/`) — listens for vanilla container packets/screens to capture storage page contents into `StorageCache`.
- `gui/component/` — reusable widgets (`PageCardComponent`, `EditDialogComponent`, `IconButtonComponent`, `StorageSlotComponent`, etc.) used by `StorageOverlayLayout`.
- `compat/RRVCompat` + `mixin/compat/*` — optional integration with the RRV (RebornStorage) mod, conditionally applied.

### Mixins

Mixins live in `mixin/` and are declared in `src/main/resources/enhancedstorage.mixins.json`. `EnhancedStorageMixinPlugin` gates the `compat.RRV*` mixins behind `FabricLoader.isModLoaded("rrv")` — RRV/Skyblocker compat mixins must stay optional at the mixin-plugin level, not just at runtime, since those mods are compile-only deps (see `modCompileOnly` entries in `build.gradle.kts`).

Non-compat mixins target core screen/input classes: `AbstractContainerScreenAccessor` (accessor), `ContainerScreenHighlightClipMixin` (implements `IHighlightClipProvider`), `MinecraftMixin`, `MouseHandlerMixin`.

### Config

`EnhancedStorageConfig` uses MidnightLib (`MidnightConfig`), giving in-game editable config with live saving via Mod Menu — there is no separate config-file-parsing code to maintain.

# Development Workflow

## Documentation

- `CLAUDE.md`: Project architecture and design overview.
- `IMPLEMENTATION_LOG.md`: Record of implementation decisions and reasoning. Keep entries concise (maximum 500 lines total).

## Workflow

### 1. Investigate First

Before writing any code, investigate the requested feature or reported issue.

- If any requirement is unclear, **stop and ask for clarification** before proceeding. Do not make assumptions. It is better to clarify early than to implement the wrong solution.
- Explain your findings briefly after the investigation.
- If the investigation naturally splits into independent tasks (for example, tracing a rendering bug, auditing mixin order, and checking overlay lifecycle), suggest running parallel sub-agents, with one agent handling each independent task. Only suggest this when it provides a meaningful speed or quality improvement.

### 2. Present a Plan

After completing the investigation:

- Present a short implementation plan or specification.
- Explain how you intend to solve the problem.
- Wait for explicit approval before writing any code.

### 3. Implement

Once approval has been given:

- Implement the planned changes.
- Keep the implementation focused on the approved scope.
- Avoid unrelated refactoring unless it is required to complete the task safely.

### 4. Validate

Before considering the work complete:

- Run all relevant tests.
- Run:

```bash
./gradlew build
```

to ensure the project builds successfully and all automated checks pass.

If manual in-game testing is required, clearly describe:

- what should be tested
- how to reproduce it
- what the expected result is

Wait for the user to complete manual testing and report back with the results before finalizing the task.

### 5. Documentation

After all testing has passed:

- Add a concise entry to `IMPLEMENTATION_LOG.md` describing:
    - what changed
    - why the change was made
    - any notable implementation decisions

- Run:

```bash
graphify update .
```

to keep the project graph up to date.

- If the work fixes a bug or introduces a user-visible feature, update `CHANGELOG.md` with a short, non-technical description suitable for end users. Avoid implementation details and internal terminology.

## General Principles

- Investigate before implementing.
- Ask for clarification instead of assuming.
- Do not write code before approval.
- Validate all changes before considering the task complete.
- Keep documentation up to date with every completed change.

### Scope Control

Only modify files that are necessary for the requested change. Avoid unrelated formatting changes, refactoring, or file reorganizations unless explicitly requested or required to complete the task safely.

### Preserve Existing Behavior

Unless the request explicitly changes existing functionality, preserve current behavior. If a proposed implementation requires changing existing behavior or introduces trade-offs, explain them in the implementation plan and wait for approval.
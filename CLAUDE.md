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

Stonecutter (multi-Minecraft-version Gradle) generates per-version project copies, so tasks can be prefixed with a version — `./gradlew :26.1:runClient` launches a dev client for that version, against a `run/` directory shared across versions.

- `./gradlew buildAndCollect` — build and copy the mod jar (+ sources jar) into `build/libs/<mod.version>/`.
- `./gradlew chiseledSrc` / stonecutter migrate tasks — used only when adding/porting to a new Minecraft version; don't invoke unless asked.

There is no separate lint or test task configured — `build` is the correctness gate (compiles, applies mixins, generates resources).

Version/mod metadata (mod id, version, dependency versions per MC version) is centralized in `stonecutter.properties.toml`, not scattered across `build.gradle.kts`. Bump versions there.

## Architecture

Client-only Fabric mod, single entrypoint: `EnhancedStorage` (`ClientModInitializer`). On init it wires up MidnightLib config, registers a profile-change listener that reloads storage caches/names and rebuilds any open overlay, and registers the `StorageCaptureHandler`.

Use `graphify query` to orient in the module graph. Non-obvious contracts:
- `StorageProfile.setOnChange` is the hook point for "player switched SkyBlock profile" — caches must be **reloaded**, not just re-read, on that event.

### Mixins

Mixins live in `mixin/` and are declared in `src/main/resources/enhancedstorage.mixins.json`. `EnhancedStorageMixinPlugin` gates the `compat.RRV*` mixins behind `FabricLoader.isModLoaded("rrv")` — RRV/Skyblocker compat mixins must stay optional at the mixin-plugin level, not just at runtime, since those mods are compile-only deps (see `modCompileOnly` entries in `build.gradle.kts`).

### Config

`EnhancedStorageConfig` uses MidnightLib (`MidnightConfig`), giving in-game editable config with live saving via Mod Menu — there is no separate config-file-parsing code to maintain.

# Development Workflow

## Documentation

- `CLAUDE.md`: Project architecture and design overview.
- `IMPLEMENTATION_LOG.md`: Record of implementation decisions and reasoning. Keep entries concise (maximum 500 lines total).

## Workflow

Investigate → plan → get approval → implement. Follow the `work-on-problem` skill for those steps; the project-specific additions are below.

### Validate

Run `./gradlew build` before considering the work complete — it is the only correctness gate.

Because this is a client mod for a live server, most behavior can only be verified in game. When manual in-game testing is required, clearly describe what should be tested, how to reproduce it, and the expected result — then wait for the user to report back before finalizing the task.

### Documentation

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

### Scope Control

Only modify files that are necessary for the requested change. Avoid unrelated formatting changes, refactoring, or file reorganizations unless explicitly requested or required to complete the task safely.

### Preserve Existing Behavior

Unless the request explicitly changes existing functionality, preserve current behavior. If a proposed implementation requires changing existing behavior or introduces trade-offs, explain them in the implementation plan and wait for approval.
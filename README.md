# Autonomous Companion (Fabric, Minecraft 1.20.1)

A client-side, Meteor-style mod: an LLM-driven agent that **controls your own player**. You type what you
want in chat; the mod captures the line (it is *not* sent to the server) and carries it out on its own.

* **Brain:** local [Ollama](https://ollama.com) (`http://localhost:11434`) running a **ReAct** loop —
  think → call one tool → read the observation → replan on failure. If Ollama is down it falls back to an offline
  rule parser for common commands.
* **Body:** [Baritone](https://github.com/cabaletta/baritone) for pathfinding/mining (driven through reflection, optional at
  load time), [Litematica](https://github.com/maruohon/litematica) for the active schematic (also reflection; falls back to
  the newest `.litematic` in `schematics/`). Meteor Client is not required — this mod plays that role itself.

## Use

Prefixes (config `triggers`, default `!`, `@ai `, `ai,`): `#` is Baritone's own prefix, so it isn't a default.

```
!gather 64 iron ore          !fetch 32 oak logs         !craft an iron pickaxe
!build                       !secure the area           !follow Steve
!go to 100 64 -200           !deliver 10 coal to Steve  !put everything away
!set waypoint home           !go to home                !status / !chests / !stop
```

**K** (rebindable, "Emergency stop") cancels everything immediately.

## What it does

| Area | Implementation |
|---|---|
| Chat + AI | `Agent`, `core/ai/*`: Ollama `/api/chat` (async, JSON mode), ReAct loop with parse/validate/repair, step cap, history trimming, replanning from `FAILURE:` observations |
| Schematics | `core/schem/*` parses `.litematic` (bit-packed palettes, multi-region, negative sizes) → materials list; `BuildTask` supplies and places **layer by layer**; modes `native` (mod places blocks) or `printer` (mod only walks/supplies, Litematica-Printer places) |
| Tools & resources | `core/craft/*`: tier rules (stone→wood pick, iron ore→stone, diamond/gold→iron, obsidian→diamond), `CraftPlanner` expands logs→planks→sticks→table→tools, cobblestone→furnace, ore→smelt→ingot; `CraftTask` runs the chain; tools that break mid-mining are re-crafted |
| Storage | `ChestTask/StoreTask/FetchTask/PlaceChestTask`: places chests in the storage zone when the inventory fills, indexes contents (`config/companion_state.json`), walks to the right chest to fetch |
| Survival | `Safety`: auto-eat, flee at low health / from creepers, melee when armed, back off from lava, surface when drowning. Baritone's `avoidance` handles lava/cliffs in pathing. The running task is paused and resumed around reflexes |
| Companion | follow a player, goto coords/waypoints, deliver items (drops them at the player), deposit to chests, `secure_area` |

## Install (step by step)

1. Install **Fabric Loader** for Minecraft **1.20.1** and put **Fabric API** in `mods/`.
2. Install **Ollama** from https://ollama.com/download (the mod cannot ship it: it is a separate program).
3. Put **Baritone** (Fabric 1.20.1 build, https://github.com/cabaletta/baritone/releases) in `mods/`. Optional: **Litematica** + **MaLiLib**.
4. Build this mod (below) and copy `build/libs/autonomous-companion-1.0.0.jar` into `mods/`.
5. Start Minecraft, join a world, and type `!setup` in chat. The mod starts `ollama serve` if needed and downloads the
   configured model (default `llama3.1:8b`, ~5 GB). Then `!help`, e.g. `!gather 10 oak logs`.

On every join the mod tells you which of Baritone/Litematica are missing. Baritone and Litematica are not bundled
(their jars could not be downloaded/verified when this was written).

## Build

```
./gradlew build        # needs network access to maven.fabricmc.net
```
Jar: `build/libs/autonomous-companion-*.jar`. Put it in `mods/` with Fabric API and (for movement/mining/building) a
Baritone build for Fabric 1.20.1; Litematica is optional.

Config: `config/companion.json` (model, URL, triggers, thresholds, `placementMode`, `printerEnableCommand`, ...).
Default model is `llama3.1:8b`; `ollama pull` any model that follows JSON instructions.

## Honest status / limitations

* The pure logic (`dev.companion.core`: Ollama client, ReAct loop, action parser, rule planner, litematic parser, crafting
  planner, chest index) has **23 unit tests that pass** (`src/test`). The Minecraft-facing code (`dev.companion.game`) was written against
  Yarn 1.20.1 **without being compiled or run** in the authoring environment (Fabric/Mojang servers were unreachable),
  so expect some compile fixes and in-game tuning on first build.
* Baritone/Litematica reflection targets their public API names; forks may differ.
* Native placement puts blocks with the item's default orientation (stairs/logs/etc. are not rotated to match the schematic);
  use `placementMode: "printer"` with Litematica-Printer for exact states. Schematic mirroring is ignored; rotation is honoured.
* Building above reach needs walkable surfaces (scaffolding is off unless `buildAllowScaffold`). Fetching from a chest takes whole stacks (may overshoot).
* Other players' chat is never obeyed; only lines you type yourself. Don't use on servers that forbid automation.

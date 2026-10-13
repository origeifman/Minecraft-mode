# Autonomous Companion: full tutorial (Windows / Mac / Linux)

You get a project folder, not a ready-made `.jar`. The mod must be compiled once on your own computer.
This takes about 10 minutes plus the model download.

## Fastest way: one command

From the unzipped project folder: **Windows** `powershell -ExecutionPolicy Bypass -File install.ps1`, **Mac/Linux** `bash install.sh`.
It installs Java/Ollama/Fabric/Fabric API/Baritone/Litematica, builds the mod, copies it into `mods` and downloads the model.
If a step prints a yellow warning, do that part by hand using Parts 1-2 below.

## Part 1 - Install the things you need

1. **Java 17 or newer**: https://adoptium.net (pick "Temurin 17" or 21). Check with `java -version` in a terminal.
2. **Minecraft Java Edition 1.20.1** (launch it once from the normal launcher).
3. **Fabric Loader**: https://fabricmc.net/use/installer/ -> install for Minecraft **1.20.1**. It creates a "fabric-loader-1.20.1" profile in the launcher.
4. **Mods folder**: open it with Win+R -> `%appdata%\.minecraft\mods` (Mac: `~/Library/Application Support/minecraft/mods`; Linux: `~/.minecraft/mods`). Create `mods` if missing.
5. Put these in the mods folder:
   * **Fabric API for 1.20.1** (https://modrinth.com/mod/fabric-api)
   * **Baritone, Fabric build for 1.20.1** (https://github.com/cabaletta/baritone/releases) - needed for walking, mining, building
   * Optional, only for schematic building: **Litematica** and **MaLiLib** for 1.20.1 (https://modrinth.com/mod/litematica, https://modrinth.com/mod/malilib)
6. **Ollama**: https://ollama.com/download -> install it. (It is the AI program; it cannot be inside the mod.)

## Part 2 - Build the mod

1. Unzip `minecraft-mode.zip` somewhere, e.g. `C:\minecraft-mode`.
2. Open a terminal **in that folder** (Windows: click the folder's address bar, type `cmd`, Enter).
3. Run (Windows) `gradlew.bat build` or (Mac/Linux) `./gradlew build`.
   The first run downloads a lot; wait for `BUILD SUCCESSFUL`.
4. Copy `build\libs\autonomous-companion-1.0.0.jar` into your `mods` folder.
   (Do NOT copy the file ending in `-sources.jar`.)

**If the build fails:** copy the red error text and send it to Claude - the game-facing code was never compiled
before, so a few small fixes are expected. This is normal, not you doing something wrong.

## Part 3 - First start

1. Launch Minecraft with the **fabric-loader-1.20.1** profile. Create a **Creative** world to test.
2. When you join, a chat message lists anything missing (Baritone / Litematica).
3. Type `!setup` in chat. The mod starts Ollama and downloads the AI model (~5 GB, several minutes). Wait for
   "Ollama is ready".
4. Type `!help`.

## Part 4 - Using it

Everything you type that starts with `!` is taken by the mod (it is not sent to the server).

| Say | What happens |
|---|---|
| `!gather 10 oak logs` | walks to trees, chops them |
| `!craft an iron pickaxe` | gathers wood, stone, iron, builds table + furnace, smelts, crafts |
| `!gather 64 iron ore` | makes the right pickaxe first if needed, then mines |
| `!set storage here` | marks where chests go |
| `!place chest` / `!deposit` | puts a chest down / stores your items; it remembers what is in each chest |
| `!fetch 32 cobblestone` | takes items back out of the right chest |
| `!build` | builds your loaded Litematica schematic layer by layer (see below) |
| `!secure the area` | kills nearby hostile mobs and guards |
| `!follow Steve` | follows another player |
| `!go to 100 64 -200` / `!set waypoint home` / `!go to home` | navigation |
| `!deliver 10 coal to Steve` | brings the items and drops them at their feet |
| `!status`, `!chests`, `!help` | information |
| `!stop` or the **K** key | emergency stop |

Automatic reflexes while it works: eats when hungry, flees at low health or from creepers, fights back if it holds a
sword/axe, backs away from lava, swims up when drowning, and stores items in chests when your inventory is full.

### Building a schematic
1. Put a `.litematic` file in `.minecraft/schematics`.
2. In Litematica, load it and **place** it where you want it (select that placement). Without Litematica the mod uses the newest file in that folder at your position.
3. Type `!build`. Have materials in your inventory or in chests, or let it gather/craft what it can.
Stairs, logs and other rotated blocks are placed with default orientation in the default mode. For exact block states use
`"placementMode": "printer"` in `config/companion.json` together with Litematica-Printer.

## Part 5 - Settings

`.minecraft/config/companion.json` (created on first start; restart the game after editing):
`model` (e.g. `"llama3.1:8b"`), `ollamaUrl`, `triggers` (change `!` if another mod uses it), `listenToAll`
(every chat line becomes a command), `eatBelowFoodLevel`, `fleeBelowHealth`, `placementMode`.

## Troubleshooting

* **Nothing happens when I type `!...`:** the mod is not loaded - check the `mods` folder and that you used the Fabric 1.20.1 profile.
* **"Ollama unreachable":** type `!setup`, or start Ollama manually (`ollama serve`). Without Ollama only simple commands work.
* **"Baritone is not installed":** put the Baritone Fabric 1.20.1 jar in `mods`.
* **It does something wrong/dangerous:** press **K**. Use it on singleplayer first; many servers forbid automation.

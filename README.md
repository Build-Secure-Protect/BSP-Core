# BSP-Core

Companion mod for the [Build Secure Protect](https://www.curseforge.com/minecraft/modpacks/build-secure-protect) modpack.
Adds the **Shatter Totem** and the base-building / raiding rules around it.

- Minecraft **1.20.1**, Forge **47.4.x**, Java 17
- Mod id: `bsp_core`
- License: MIT

## Building

```bash
./gradlew build
```

The JAR is written to `build/libs/bsp-core-<version>.jar`.

Other useful tasks: `./gradlew runClient`, `./gradlew runServer`, `./gradlew runData`.

## Controls

| Action | How |
|---|---|
| Open the upgrade tree | Right-click with the totem in your main hand |
| Place the totem | Sneak + right-click a block |
| Get buffs | Keep the totem in your offhand |
| Open a placed totem's panel / steal it | Right-click the block |
| Pick up your placed totem | Mine it (owner or operator only) |
| Buy Fortify / Healing Aura | Open your placed totem's panel with Shatter Coins in your inventory |
| Log out | Carried totems are placed where you stood; the pause menu warns you first |
| Operator controls | Right-click any placed totem as an operator: purple column on the left |

## Operator commands

| Command | Effect |
|---|---|
| `/bsp totem give <player>` | Give a fresh totem owned by that player |
| `/bsp totem reset <player>` | Forget the first-join grant so the player is granted again |
| `/bsp totem locate <player>` | List where that player's placed totems stand |
| `/bsp totem owner` | Show the owner of the totem in your main hand |

## Config

Server config is generated at `<world>/serverconfig/bsp_core-server.toml`.

| Key | Default | Meaning |
|---|---|---|
| `first_join.grantTotemOnFirstJoin` | `false` | Give a Shatter Totem on first join. Enable only on the Spawn Hub server. |
| `upgrades.damageXpLevelCosts` | `[5, 10, 20, 35, 55]` | XP level cost of each Damage buff level. List length is the max level. |
| `upgrades.resistanceXpLevelCosts` | `[5, 10, 20, 35, 55]` | Same for Resistance. |
| `upgrades.miningSpeedXpLevelCosts` | `[5, 10, 20, 35, 55]` | Same for Mining Speed. |
| `upgrades.fortifyCoinCosts` / `healingCoinCosts` | `[5, 10, 20, 40, 80]` | Shatter Coin cost per level of the placed-only upgrades. |
| `upgrades.fortifyRadius` | `[1, 3, 5, 7, 15]` | Fortify radius per level. |
| `upgrades.fortifyBreakSpeedMultiplier` | `[0.6, 0.45, 0.3, 0.2, 0.1]` | Non-owner mining speed inside a Fortify aura. |
| `upgrades.fortifyExplosionProtection` | `[0.3, 0.5, 0.7, 0.85, 1.0]` | Chance each block in range survives an explosion. |
| `upgrades.healingRadius` | `[3, 5, 7, 10, 15]` | Healing Aura radius per level. |
| `upgrades.healingPerSecond` | `[0.5, 1, 1.5, 2, 3]` | Health healed per second (2 = one heart). |
| `restrictions.allowedDimensions` | overworld, nether, end | Where the totem may be placed, dropped or auto-placed. |
| `restrictions.groundSecondsBeforePlace` | `30` | How long a dropped totem waits before placing itself. |
| `steal.stealSeconds` | `300` | Time a thief must stay near a totem. |
| `steal.radiusBlocks` | `5` | Radius the thief must stay within. |
| `steal.graceSeconds` | `15` | Time allowed outside the radius before the steal fails. |
| `steal.unclaimedStealSeconds` | `60` | Time needed to claim a placed totem that has no owner. |
| `steal.invincibilitySeconds` | `15` | Invincibility given to the thief when a steal completes. |
| `steal.warningSeconds` | `30` | Remaining time at which the steal timer and bar pulse red. |

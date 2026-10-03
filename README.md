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

## Config

Server config is generated at `<world>/serverconfig/bsp_core-server.toml`.

| Key | Default | Meaning |
|---|---|---|
| `first_join.grantTotemOnFirstJoin` | `false` | Give a Shatter Totem on first join. Enable only on the Spawn Hub server. |

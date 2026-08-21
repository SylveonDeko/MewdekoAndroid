The Official Android App for Mewdeko

https://mewdeko.tech/

----

MewdekoMobile is the native Android client for the [Mewdeko](https://github.com/SylveonDeko/Mewdeko) Discord bot. It talks to the same dashboard API as [MewdekoDash](https://github.com/SylveonDeko/MewdekoDash), letting server owners manage bot settings and view statistics from their phone instead of a browser or Discord commands.

Built with Kotlin, Jetpack Compose, and Material 3.

## Features

Every dashboard feature is ported natively, including moderation, XP, music, tickets, forms, embed and component building, chat triggers, giveaways, starboard, suggestions, custom voice, Minecraft integration, and account/reputation/currency management. The bottom navigation and per-guild theming follow the dashboard's own mobile layout.

## Requirements

- Android Studio (or a JDK 21 + Android SDK toolchain)
- A running Mewdeko bot instance behind a dashboard that exposes the mobile OAuth endpoints (see [MewdekoDash](https://github.com/SylveonDeko/MewdekoDash)), or the hosted dashboard at mewdeko.tech

## Building

```bash
./gradlew :app:assembleDebug
```

The debug build installs alongside the release build (`applicationIdSuffix = ".debug"`), so both can be on the same device at once.

```bash
./gradlew :app:installDebug
```

### Release signing

Release builds are unsigned unless a keystore is configured. Copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill it in, or supply `MEWDEKO_KEYSTORE`, `MEWDEKO_KEYSTORE_PASSWORD`, `MEWDEKO_KEY_ALIAS`, and `MEWDEKO_KEY_PASSWORD` as environment variables, which is what CI uses.

```bash
./gradlew :app:assembleRelease
```

## Project structure

- `app/src/main/java/dev/mewdeko/mobile/core/` — networking, auth, theming, and shared UI components used across every feature
- `app/src/main/java/dev/mewdeko/mobile/feature/` — one package per dashboard feature
- `app/src/main/java/dev/mewdeko/mobile/navigation/` — the nav graph, route catalog, and bottom bars

## Support

For help, reach out in the [Mewdeko Discord Server](https://discord.gg/nh9WWPvnde) or open an issue on GitHub.

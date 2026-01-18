# AntryDeathLoot

A high-quality Minecraft death chest plugin compatible with **Minecraft 1.7.10 to 1.21+**.

When a player dies, their items are stored in a falling chest that descends from the sky with a hologram timer showing who the loot belongs to and how much time remains before the chest breaks open.

## Features

- ✅ **Wide Version Support**: Works from Minecraft 1.7.10 to 1.21+
- ✅ **Falling Chest Animation**: Chest falls from the sky when a player dies
- ✅ **Hologram Display**: Shows player name and countdown timer (1.8+)
- ✅ **Auto-Break Timer**: Chest automatically breaks after configurable time
- ✅ **Safe Location**: Automatically finds safe ground for chest placement
- ✅ **Permission System**: Control who can create/break death chests
- ✅ **Fully Configurable**: Messages, timers, and features all customizable
- ✅ **Lightweight**: No external dependencies required

## Version Compatibility

| Feature | 1.7.10 | 1.8-1.8.8 | 1.9-1.12 | 1.13-1.20 | 1.21+ |
|---------|--------|-----------|----------|-----------|-------|
| Death Chests | ✅ | ✅ | ✅ | ✅ | ✅ |
| Falling Animation | ✅ | ✅ | ✅ | ✅ | ✅ |
| Holograms | ❌ | ✅ | ✅ | ✅ | ✅ |
| Sounds | Basic | Full | Full | Full | Full |

> **Note**: Holograms use ArmorStands which were added in Minecraft 1.8. On 1.7.10 servers, holograms are automatically disabled.

## Installation

1. Download the latest `AntryDeathLoot.jar` from releases
2. Place it in your server's `plugins` folder
3. Restart your server
4. Configure in `plugins/AntryDeathLoot/config.yml`

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/antrydeatloot` | Show plugin help | None |
| `/antrydeatloot reload` | Reload configuration | `antrydeatloot.admin` |
| `/antrydeatloot info` | Show plugin info | None |

**Aliases**: `/adl`, `/deathchest`, `/dc`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `antrydeatloot.*` | All permissions | OP |
| `antrydeatloot.create` | Death chest created on death | Everyone |
| `antrydeatloot.break` | Can break death chests early | Everyone |
| `antrydeatloot.admin` | Admin commands (reload) | OP |

## Configuration

```yaml
# Plugin prefix for messages
prefix: "&f&l[&3&lAntryDeathLoot&f&l] "

# Time before chest breaks (seconds)
chest-break-time: 10

# Allow players to break chests early
allow-instant-break: true

# Broadcast when chests are created
announce-death-chest: true

# Death message with placeholders
death-chest-message: "&c%player%'s death chest has been created! It will break in %time% seconds!"

# Message when chest breaks
chest-break-message: "&cDeath chest is breaking!"

# Falling animation settings
falling-chest:
  enabled: true
  height: 20

# Hologram settings (requires 1.8+)
hologram:
  enabled: true
  height: 1.0
  line-spacing: 0.3
  first-line: "&7%player%'s &fLoot"
  second-line: "&fTime remaining: &c%seconds%s"
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/AntryDeathLoot.git
cd AntryDeathLoot

# Build with Maven
mvn clean package

# The jar will be in target/AntryDeathLoot-1.1.0.jar
```

### Requirements
- Java 8 or higher
- Maven 3.6+

## Technical Details

### Version Detection
The plugin automatically detects your server version and adjusts functionality:
- Uses reflection-free version parsing from Bukkit version string
- Caches version checks for performance
- Gracefully handles unknown/future versions

### Safe Location Finding
When a player dies in dangerous locations (void, lava, water, mid-air), the plugin finds the nearest safe location for the chest by:
1. Checking the death location
2. Searching downward for solid ground
3. Searching upward if no ground below
4. Ensuring the location can hold a chest

### Thread Safety
- Uses `ConcurrentHashMap` for all chest tracking
- Tasks are properly cancelled on plugin disable
- Falling chest monitors are cleaned up automatically

## Support

If you encounter issues:
1. Check the console for error messages
2. Ensure you're running a supported Minecraft version
3. Verify your config.yml is valid YAML
4. Open an issue on GitHub with:
   - Server version
   - Plugin version
   - Console logs
   - Steps to reproduce

## License

This project is open source. Feel free to use, modify, and distribute.

## Credits

- **Author**: Antry
- **Contributors**: Arctic Development Team

package dev.arctic.arcticdeathchest.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FallingBlock;
import org.bukkit.Location;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to handle version compatibility between Minecraft 1.7.10 and 1.21+
 * Provides safe methods for cross-version functionality.
 */
public class VersionUtils {
    private static final Logger logger = Bukkit.getLogger();
    private static final String VERSION = Bukkit.getVersion();
    private static final String BUKKIT_VERSION = Bukkit.getBukkitVersion();
    
    // Version parsing cache
    private static int majorVersion = -1;
    private static int minorVersion = -1;
    private static int patchVersion = -1;
    
    // Feature support cache
    private static Boolean isLegacy = null;
    private static Boolean hasNewSoundSystem = null;
    private static Boolean hasNewMaterialSystem = null;
    private static Boolean supportsArmorStands = null;
    private static Boolean supportsArmorStandMarker = null;
    
    // Parse version once on load
    static {
        parseVersion();
    }
    
    /**
     * Parse the Minecraft version from Bukkit version string
     */
    private static void parseVersion() {
        try {
            // Pattern to match versions like "1.8.8-R0.1-SNAPSHOT" or "1.21.1-R0.1-SNAPSHOT"
            Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
            Matcher matcher = pattern.matcher(BUKKIT_VERSION);
            
            if (matcher.find()) {
                majorVersion = Integer.parseInt(matcher.group(1));
                minorVersion = Integer.parseInt(matcher.group(2));
                patchVersion = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            } else {
                // Fallback - assume 1.8
                majorVersion = 1;
                minorVersion = 8;
                patchVersion = 0;
                logger.warning("Could not parse version from: " + BUKKIT_VERSION + ", defaulting to 1.8");
            }
        } catch (Exception e) {
            majorVersion = 1;
            minorVersion = 8;
            patchVersion = 0;
            logger.warning("Error parsing version: " + e.getMessage());
        }
    }
    
    /**
     * Check if running on at least the specified version
     */
    public static boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }
    
    /**
     * Check if running on at least the specified version
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (majorVersion > major) return true;
        if (majorVersion < major) return false;
        if (minorVersion > minor) return true;
        if (minorVersion < minor) return false;
        return patchVersion >= patch;
    }
    
    /**
     * Check if the server version is supported (1.7.10+)
     */
    public static boolean isVersionSupported() {
        return majorVersion >= 1 && (minorVersion >= 7 || majorVersion > 1);
    }
    
    /**
     * Check if we're running on a legacy version (< 1.13)
     */
    public static boolean isLegacyVersion() {
        if (isLegacy == null) {
            isLegacy = !isAtLeast(1, 13);
        }
        return isLegacy;
    }
    
    /**
     * Check if the server has the new sound system (1.9+)
     */
    public static boolean hasNewSoundSystem() {
        if (hasNewSoundSystem == null) {
            if (!isAtLeast(1, 9)) {
                hasNewSoundSystem = false;
            } else {
                // Double-check by trying to access a 1.9+ sound
                try {
                    Sound.valueOf("BLOCK_CHEST_OPEN");
                    hasNewSoundSystem = true;
                } catch (IllegalArgumentException e) {
                    hasNewSoundSystem = false;
                }
            }
        }
        return hasNewSoundSystem;
    }
    
    /**
     * Check if the server has the new material system (1.13+)
     */
    public static boolean hasNewMaterialSystem() {
        if (hasNewMaterialSystem == null) {
            hasNewMaterialSystem = isAtLeast(1, 13);
        }
        return hasNewMaterialSystem;
    }
    
    /**
     * Check if ArmorStands are supported (1.8+)
     */
    public static boolean supportsArmorStands() {
        if (supportsArmorStands == null) {
            supportsArmorStands = isAtLeast(1, 8);
        }
        return supportsArmorStands;
    }
    
    /**
     * Check if ArmorStand.setMarker() is supported (1.8.1+)
     */
    public static boolean supportsArmorStandMarker() {
        if (supportsArmorStandMarker == null) {
            // setMarker was added in 1.8.1
            supportsArmorStandMarker = isAtLeast(1, 8, 1);
        }
        return supportsArmorStandMarker;
    }
    
    /**
     * Get the chest open sound for the current version
     */
    public static Sound getChestOpenSound() {
        return getSoundSafe("BLOCK_CHEST_OPEN", "CHEST_OPEN", "CLICK");
    }
    
    /**
     * Get the chest close sound for the current version
     */
    public static Sound getChestCloseSound() {
        return getSoundSafe("BLOCK_CHEST_CLOSE", "CHEST_CLOSE", "CLICK");
    }
    
    /**
     * Get the block break sound for the current version
     */
    public static Sound getBlockBreakSound() {
        return getSoundSafe("BLOCK_WOOD_BREAK", "DIG_WOOD", "CLICK");
    }
    
    /**
     * Get the item pickup sound for the current version
     */
    public static Sound getItemPickupSound() {
        return getSoundSafe("ENTITY_ITEM_PICKUP", "ITEM_PICKUP", "CLICK");
    }
    
    /**
     * Safely get a sound, trying multiple fallbacks
     */
    private static Sound getSoundSafe(String modernSound, String legacySound, String fallbackSound) {
        // Try modern sound first (1.9+)
        if (hasNewSoundSystem()) {
            try {
                return Sound.valueOf(modernSound);
            } catch (IllegalArgumentException ignored) {}
        }
        
        // Try legacy sound (1.8.x)
        try {
            return Sound.valueOf(legacySound);
        } catch (IllegalArgumentException ignored) {}
        
        // Try fallback sound (1.7.10)
        try {
            return Sound.valueOf(fallbackSound);
        } catch (IllegalArgumentException e) {
            return null; // No sound available
        }
    }
    
    /**
     * Create a falling block in a version-compatible way
     */
    @SuppressWarnings("deprecation")
    public static FallingBlock spawnFallingBlock(Location location, Material material) {
        if (location == null || location.getWorld() == null || material == null) {
            return null;
        }
        
        try {
            // For all versions, use the deprecated method with data value
            // This works from 1.7.10 to 1.21+
            return location.getWorld().spawnFallingBlock(location, material, (byte) 0);
        } catch (Exception e) {
            logger.warning("Failed to spawn falling block: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Set FallingBlock to not drop items (version compatible)
     */
    public static void setFallingBlockNoDrop(FallingBlock fallingBlock) {
        if (fallingBlock == null) return;
        
        try {
            fallingBlock.setDropItem(false);
        } catch (Exception e) {
            // Some very old versions might not have this method
            logger.fine("Could not set falling block drop item: " + e.getMessage());
        }
    }
    
    /**
     * Get a safe spawn location for a chest (avoid water, lava, air with no support)
     */
    public static Location getSafeChestLocation(Location deathLocation) {
        if (deathLocation == null || deathLocation.getWorld() == null) {
            return null;
        }
        
        Location safe = deathLocation.getBlock().getLocation();
        
        // Check if current location is safe
        Material type = safe.getBlock().getType();
        
        // If in void, try to find ground above
        if (safe.getY() < 0) {
            safe.setY(1);
        }
        
        // If in liquid or air, search for ground below
        if (isLiquid(type) || type == Material.AIR) {
            // Search downward for solid ground
            for (int y = safe.getBlockY(); y > 0; y--) {
                safe.setY(y);
                Material below = safe.getBlock().getRelative(0, -1, 0).getType();
                Material at = safe.getBlock().getType();
                
                if (isSolid(below) && (at == Material.AIR || isReplaceable(at))) {
                    return safe;
                }
            }
            
            // Search upward if couldn't find ground below
            for (int y = deathLocation.getBlockY(); y < deathLocation.getWorld().getMaxHeight(); y++) {
                safe.setY(y);
                Material below = safe.getBlock().getRelative(0, -1, 0).getType();
                Material at = safe.getBlock().getType();
                
                if (isSolid(below) && (at == Material.AIR || isReplaceable(at))) {
                    return safe;
                }
            }
        }
        
        return safe;
    }
    
    /**
     * Check if a material is a liquid
     */
    private static boolean isLiquid(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.contains("WATER") || name.contains("LAVA");
    }
    
    /**
     * Check if a material is solid (can support a chest)
     */
    private static boolean isSolid(Material material) {
        if (material == null) return false;
        return material.isSolid() && !isLiquid(material);
    }
    
    /**
     * Check if a material can be replaced by a chest
     */
    private static boolean isReplaceable(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.contains("GRASS") || name.contains("FLOWER") || 
               name.contains("TALL_") || name.equals("SNOW") || 
               name.contains("MUSHROOM") || material == Material.AIR;
    }
    
    /**
     * Get version information string
     */
    public static String getVersionInfo() {
        return String.format("%d.%d.%d (Bukkit: %s)", 
                           majorVersion, minorVersion, patchVersion, BUKKIT_VERSION);
    }
    
    /**
     * Get the detected Minecraft version as a string
     */
    public static String getMinecraftVersion() {
        return majorVersion + "." + minorVersion + "." + patchVersion;
    }
    
    /**
     * Check if the current version supports a specific feature
     */
    public static boolean supportsFeature(String feature) {
        switch (feature.toLowerCase()) {
            case "armor_stands":
                return supportsArmorStands();
            case "armor_stand_marker":
            case "armor_stand_small":
            case "armor_stand_baseplate":
                return supportsArmorStandMarker();
            case "custom_name_visible":
                return supportsArmorStands();
            case "new_materials":
            case "block_data":
                return hasNewMaterialSystem();
            case "new_sounds":
                return hasNewSoundSystem();
            case "holograms":
                return supportsArmorStands();
            case "falling_blocks":
                return true; // Available since 1.7.10
            case "particle_api":
                return isAtLeast(1, 9);
            case "boss_bar":
                return isAtLeast(1, 9);
            case "action_bar":
                return isAtLeast(1, 8);
            default:
                return true;
        }
    }
    
    /**
     * Log version compatibility information
     */
    public static void logVersionInfo() {
        logger.info("=== AntryDeathLoot Version Compatibility ===");
        logger.info("Detected Minecraft version: " + getMinecraftVersion());
        logger.info("Bukkit version: " + BUKKIT_VERSION);
        logger.info("Full version string: " + VERSION);
        logger.info("Features:");
        logger.info("  - Legacy mode (pre-1.13): " + isLegacyVersion());
        logger.info("  - New sound system (1.9+): " + hasNewSoundSystem());
        logger.info("  - New material system (1.13+): " + hasNewMaterialSystem());
        logger.info("  - ArmorStand support (1.8+): " + supportsArmorStands());
        logger.info("  - ArmorStand marker (1.8.1+): " + supportsArmorStandMarker());
        logger.info("==========================================");
    }
}

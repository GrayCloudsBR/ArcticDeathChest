package dev.arctic.arcticdeathchest.managers;

import dev.arctic.arcticdeathchest.ArcticDeathChest;
import dev.arctic.arcticdeathchest.utils.VersionUtils;
import lombok.extern.java.Log;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manager for creating and handling hologram displays above death chests.
 * Uses ArmorStands with custom names for compatibility across versions.
 * Note: ArmorStands are only available in Minecraft 1.8+
 */
@Log
public class HologramManager {
    private static ArcticDeathChest plugin;
    private static final Map<Location, List<ArmorStand>> activeHolograms = new ConcurrentHashMap<>();
    private static boolean hologramsSupported = false;

    public static void initialize(ArcticDeathChest main) {
        plugin = main;
        
        // Check if ArmorStands are supported
        hologramsSupported = VersionUtils.supportsFeature("armor_stands");
        
        if (!hologramsSupported) {
            log.info("HologramManager initialized - Holograms DISABLED (requires Minecraft 1.8+)");
        } else {
            log.info("HologramManager initialized - Holograms ENABLED");
        }
    }
    
    /**
     * Check if holograms are supported on this server version
     */
    public static boolean isSupported() {
        return hologramsSupported;
    }

    /**
     * Create a hologram above a death chest
     * @param location The location of the chest
     * @param player The player who owns the chest
     * @param seconds Initial countdown seconds
     * @return List of ArmorStands forming the hologram, or empty list if not supported
     */
    public static List<ArmorStand> createHologram(Location location, Player player, int seconds) {
        List<ArmorStand> hologramLines = new ArrayList<>();
        
        // Check all prerequisites
        if (!hologramsSupported || plugin == null || plugin.getPluginConfig() == null || 
            !plugin.getPluginConfig().isHologramEnabled() || location == null || 
            location.getWorld() == null || player == null) {
            return hologramLines;
        }
        
        try {
            double height = plugin.getPluginConfig().getHologramHeight();
            double lineSpacing = plugin.getPluginConfig().getHologramLineSpacing();
            
            // Position hologram at center of block, above chest
            Location holoLoc = location.getBlock().getLocation().add(0.5, height + 1.0, 0.5);
            holoLoc.setYaw(0);
            holoLoc.setPitch(0);
            
            // Create first line (player name)
            String firstLine = plugin.getPluginConfig().getHologramFirstLine()
                                   .replace("%player%", player.getName());
            firstLine = ChatColor.translateAlternateColorCodes('&', firstLine);
            ArmorStand firstStand = spawnHologramLine(holoLoc.clone(), firstLine);
            if (firstStand != null) {
                hologramLines.add(firstStand);
            }
            
            // Create second line (timer)
            String secondLine = plugin.getPluginConfig().getHologramSecondLine()
                                    .replace("%seconds%", String.valueOf(seconds));
            secondLine = ChatColor.translateAlternateColorCodes('&', secondLine);
            ArmorStand secondStand = spawnHologramLine(holoLoc.clone().subtract(0, lineSpacing, 0), secondLine);
            if (secondStand != null) {
                hologramLines.add(secondStand);
            }
            
            // Track the hologram for cleanup
            if (!hologramLines.isEmpty()) {
                activeHolograms.put(normalizeLocation(location), new ArrayList<>(hologramLines));
                log.fine("Created hologram with " + hologramLines.size() + " lines for " + player.getName());
            }
            
        } catch (Exception e) {
            log.warning("Error creating hologram: " + e.getMessage());
            // Clean up any partially created holograms
            removeHologram(hologramLines);
            hologramLines.clear();
        }
        
        return hologramLines;
    }

    /**
     * Spawn a single hologram line at the specified location
     */
    private static ArmorStand spawnHologramLine(Location location, String text) {
        try {
            if (location.getWorld() == null) {
                log.warning("Cannot spawn hologram in null world");
                return null;
            }
            
            ArmorStand hologram = location.getWorld().spawn(location, ArmorStand.class);
            
            // Configure ArmorStand as hologram - use try-catch for version compatibility
            hologram.setVisible(false);
            hologram.setGravity(false);
            hologram.setCustomName(text);
            hologram.setCustomNameVisible(true);
            
            // These methods might not exist in older versions
            try {
                hologram.setCanPickupItems(false);
            } catch (NoSuchMethodError ignored) {
                // Method doesn't exist in this version
            }
            
            // setMarker, setSmall, setBasePlate, setArms were added in 1.8.1
            if (VersionUtils.supportsArmorStandMarker()) {
                try {
                    hologram.setMarker(true);
                    hologram.setSmall(true);
                    hologram.setBasePlate(false);
                    hologram.setArms(false);
                } catch (NoSuchMethodError e) {
                    // Methods don't exist in this version
                    log.fine("Some ArmorStand methods not available: " + e.getMessage());
                }
            }
            
            return hologram;
        } catch (Exception e) {
            log.warning("Failed to spawn hologram line: " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove a hologram (list of ArmorStands)
     */
    public static void removeHologram(List<ArmorStand> hologramLines) {
        if (hologramLines == null) {
            return;
        }
        
        try {
            int removed = 0;
            for (ArmorStand stand : hologramLines) {
                if (stand != null) {
                    try {
                        if (stand.isValid() && !stand.isDead()) {
                            stand.remove();
                            removed++;
                        }
                    } catch (Exception e) {
                        // Entity might already be removed
                        log.fine("Could not remove armor stand: " + e.getMessage());
                    }
                }
            }
            hologramLines.clear();
            
            if (removed > 0) {
                log.fine("Removed " + removed + " hologram entities");
            }
        } catch (Exception e) {
            log.warning("Error removing hologram: " + e.getMessage());
        }
    }

    /**
     * Update the timer display on a hologram
     */
    public static void updateTimer(List<ArmorStand> hologramLines, int seconds) {
        if (hologramLines == null || hologramLines.size() < 2 || 
            plugin == null || plugin.getPluginConfig() == null) {
            return;
        }
        
        try {
            ArmorStand timerLine = hologramLines.get(1);
            if (timerLine != null && timerLine.isValid() && !timerLine.isDead()) {
                String secondLine = plugin.getPluginConfig().getHologramSecondLine()
                                        .replace("%seconds%", String.valueOf(seconds));
                secondLine = ChatColor.translateAlternateColorCodes('&', secondLine);
                timerLine.setCustomName(secondLine);
            }
        } catch (Exception e) {
            log.warning("Error updating hologram timer: " + e.getMessage());
        }
    }
    
    /**
     * Normalize a location to block coordinates for map key consistency
     */
    private static Location normalizeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    /**
     * Clean up all active holograms and reset the manager
     */
    public static void cleanup() {
        try {
            int count = activeHolograms.size();
            if (count > 0) {
                log.info("Cleaning up " + count + " active holograms...");
            }
            
            for (List<ArmorStand> hologram : activeHolograms.values()) {
                removeHologram(hologram);
            }
            activeHolograms.clear();
            
            if (count > 0) {
                log.info("Hologram cleanup completed.");
            }
        } catch (Exception e) {
            log.severe("Error during hologram cleanup: " + e.getMessage());
            e.printStackTrace();
            
            // Force clear as fallback
            activeHolograms.clear();
        }
    }
    
    /**
     * Remove a hologram from tracking when it's destroyed
     * @param location The location of the hologram to stop tracking
     */
    public static void untrackHologram(Location location) {
        Location normalized = normalizeLocation(location);
        if (normalized != null) {
            List<ArmorStand> removed = activeHolograms.remove(normalized);
            if (removed != null) {
                log.fine("Untracked hologram at " + normalized);
            }
        }
    }
    
    /**
     * Get the number of active holograms
     * @return count of active holograms
     */
    public static int getActiveHologramCount() {
        return activeHolograms.size();
    }
}

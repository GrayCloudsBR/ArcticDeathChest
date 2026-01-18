package dev.arctic.arcticdeathchest.config;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * Immutable configuration class for ArcticDeathChest.
 * Uses Lombok for boilerplate reduction.
 */
@Data
@Builder
public class PluginConfig {
    @NonNull
    private final String prefix;
    
    private final int chestBreakTime;
    private final boolean allowInstantBreak;
    private final boolean announceDeathChest;
    
    @NonNull
    private final String deathChestMessage;
    
    private final String chestBreakMessage;
    
    // Falling chest settings
    private final boolean fallingChestEnabled;
    private final int fallingChestHeight;
    
    // Hologram settings
    private final boolean hologramEnabled;
    private final double hologramHeight;
    private final double hologramLineSpacing;
    
    @NonNull
    private final String hologramFirstLine;
    
    @NonNull
    private final String hologramSecondLine;
    
    /**
     * Load configuration from Bukkit FileConfiguration
     * @param config the file configuration
     * @param logger the logger for warnings
     * @return PluginConfig instance
     */
    public static PluginConfig fromFileConfiguration(@NonNull FileConfiguration config, Logger logger) {
        // Read values with defaults
        String prefix = config.getString("prefix", "&f&l[&3&lArcticDeathChest&f&l] ");
        int chestBreakTime = config.getInt("chest-break-time", 10);
        boolean allowInstantBreak = config.getBoolean("allow-instant-break", true);
        boolean announceDeathChest = config.getBoolean("announce-death-chest", true);
        String deathChestMessage = config.getString("death-chest-message", 
            "&c%player%'s death chest has been created! It will break in %time% seconds!");
        String chestBreakMessage = config.getString("chest-break-message", "&cDeath chest is breaking!");
        
        // Falling chest settings
        boolean fallingChestEnabled = config.getBoolean("falling-chest.enabled", true);
        int fallingChestHeight = config.getInt("falling-chest.height", 20);
        
        // Hologram settings
        boolean hologramEnabled = config.getBoolean("hologram.enabled", true);
        double hologramHeight = config.getDouble("hologram.height", 1.0);
        double hologramLineSpacing = config.getDouble("hologram.line-spacing", 0.3);
        String hologramFirstLine = config.getString("hologram.first-line", "&7%player%'s &fLoot");
        String hologramSecondLine = config.getString("hologram.second-line", "&fTime remaining: &c%seconds%s");
        
        // Validate and adjust values
        if (chestBreakTime < 1) {
            if (logger != null) {
                logger.warning("chest-break-time must be at least 1, using default of 10");
            }
            chestBreakTime = 10;
        }
        
        if (fallingChestHeight < 1) {
            if (logger != null) {
                logger.warning("falling-chest.height must be at least 1, using default of 20");
            }
            fallingChestHeight = 20;
        }
        
        if (hologramHeight < 0) {
            if (logger != null) {
                logger.warning("hologram.height cannot be negative, using default of 1.0");
            }
            hologramHeight = 1.0;
        }
        
        if (hologramLineSpacing < 0) {
            if (logger != null) {
                logger.warning("hologram.line-spacing cannot be negative, using default of 0.3");
            }
            hologramLineSpacing = 0.3;
        }
        
        return PluginConfig.builder()
            .prefix(prefix)
            .chestBreakTime(chestBreakTime)
            .allowInstantBreak(allowInstantBreak)
            .announceDeathChest(announceDeathChest)
            .deathChestMessage(deathChestMessage)
            .chestBreakMessage(chestBreakMessage)
            .fallingChestEnabled(fallingChestEnabled)
            .fallingChestHeight(fallingChestHeight)
            .hologramEnabled(hologramEnabled)
            .hologramHeight(hologramHeight)
            .hologramLineSpacing(hologramLineSpacing)
            .hologramFirstLine(hologramFirstLine)
            .hologramSecondLine(hologramSecondLine)
            .build();
    }
    
    /**
     * Check if the configuration is valid
     * @return true if valid
     */
    public boolean isValid() {
        return prefix != null && !prefix.isEmpty() &&
               chestBreakTime > 0 && 
               hologramHeight >= 0 && 
               hologramLineSpacing >= 0 && 
               fallingChestHeight > 0 &&
               deathChestMessage != null &&
               hologramFirstLine != null &&
               hologramSecondLine != null;
    }
}

package dev.arctic.arcticdeathchest;

import dev.arctic.arcticdeathchest.config.PluginConfig;
import dev.arctic.arcticdeathchest.managers.DeathChestManager;
import dev.arctic.arcticdeathchest.managers.MessageManager;
import dev.arctic.arcticdeathchest.managers.HologramManager;
import dev.arctic.arcticdeathchest.utils.VersionUtils;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.ArrayList;
import java.util.List;

@Log
public final class ArcticDeathChest extends JavaPlugin implements Listener {
    
    @Getter
    private DeathChestManager deathChestManager;
    
    @Getter
    private PluginConfig pluginConfig;
    
    private volatile boolean isShuttingDown = false;

    @Override
    public void onEnable() {
        try {
            // Log version compatibility information
            VersionUtils.logVersionInfo();
            
            // Check minimum version requirements
            if (!VersionUtils.isVersionSupported()) {
                getLogger().severe("This plugin requires Minecraft 1.7.10 or higher!");
                setEnabled(false);
                return;
            }
            
            // Save default config
            saveDefaultConfig();
            
            // Load configuration
            this.pluginConfig = PluginConfig.fromFileConfiguration(getConfig(), getLogger());
            
            if (!pluginConfig.isValid()) {
                getLogger().severe("Invalid configuration detected! Please check your config.yml");
                setEnabled(false);
                return;
            }
            
            // Initialize managers
            this.deathChestManager = new DeathChestManager(this);
            MessageManager.initialize(this);
            HologramManager.initialize(this);
            
            // Register events
            getServer().getPluginManager().registerEvents(this, this);
            
            log.info("ArcticDeathChest v" + getDescription().getVersion() + " has been enabled!");
            log.info("Configuration loaded - Break time: " + pluginConfig.getChestBreakTime() + "s, " +
                    "Holograms: " + (pluginConfig.isHologramEnabled() ? "enabled" : "disabled") + ", " +
                    "Falling chests: " + (pluginConfig.isFallingChestEnabled() ? "enabled" : "disabled"));
            
            // Log version-specific feature availability
            if (!VersionUtils.supportsFeature("armor_stands")) {
                log.info("Note: Holograms are disabled on this version (requires 1.8+)");
            }
                    
        } catch (Exception e) {
            log.severe("Failed to enable ArcticDeathChest: " + e.getMessage());
            e.printStackTrace();
            setEnabled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isShuttingDown || deathChestManager == null || pluginConfig == null) {
            return;
        }
        
        // Check permission
        if (!event.getEntity().hasPermission("arcticdeathchest.create")) {
            return;
        }
        
        try {
            // Get original drops
            List<ItemStack> originalDrops = new ArrayList<>(event.getDrops());
            
            // Only create chest if there are items to store
            if (originalDrops.isEmpty()) {
                return;
            }
            
            // Clear default drops
            event.getDrops().clear();
            
            // Create death chest with the copied drops
            boolean success = deathChestManager.createDeathChest(
                event.getEntity(),
                event.getEntity().getLocation(),
                originalDrops
            );
            
            if (success) {
                // Send message
                MessageManager.sendDeathChestMessage(event.getEntity());
            } else {
                // Restore drops if chest creation failed
                event.getDrops().addAll(originalDrops);
                log.warning("Failed to create death chest for " + event.getEntity().getName() + ", restoring drops");
            }
        } catch (Exception e) {
            log.warning("Error handling player death for " + event.getEntity().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isShuttingDown || deathChestManager == null || event.getBlock().getType() != Material.CHEST) {
            return;
        }
        
        try {
            Location location = event.getBlock().getLocation();
            if (deathChestManager.isDeathChest(location)) {
                event.setCancelled(true);
                
                // Check if player can break this chest
                if (!pluginConfig.isAllowInstantBreak()) {
                    MessageManager.sendMessage(event.getPlayer(), "&cYou cannot break this death chest!");
                    return;
                }
                
                // Check permission to break
                if (!event.getPlayer().hasPermission("arcticdeathchest.break")) {
                    MessageManager.sendMessage(event.getPlayer(), "&cYou don't have permission to break death chests!");
                    return;
                }
                
                deathChestManager.cancelBreakTask(location);
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!isShuttingDown) {
                        deathChestManager.breakChest(location);
                    }
                });
            }
        } catch (Exception e) {
            log.warning("Error handling block break: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isShuttingDown || deathChestManager == null || event.getBlock().getType() != Material.CHEST) {
            return;
        }
        
        try {
            Location location = event.getBlock().getLocation();
            if (deathChestManager.isDeathChest(location)) {
                if (!pluginConfig.isAllowInstantBreak()) {
                    return;
                }
                
                if (!event.getPlayer().hasPermission("arcticdeathchest.break")) {
                    return;
                }
                
                event.setInstaBreak(true);
            }
        } catch (Exception e) {
            log.warning("Error handling block damage: " + e.getMessage());
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("arcticdeathchest")) {
            return false;
        }
        
        if (args.length == 0) {
            sender.sendMessage(MessageManager.colorize("&3&lArcticDeathChest &fv" + getDescription().getVersion()));
            sender.sendMessage(MessageManager.colorize("&7/arcticdeathchest reload &f- Reload configuration"));
            sender.sendMessage(MessageManager.colorize("&7/arcticdeathchest info &f- Show plugin info"));
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("arcticdeathchest.admin")) {
                    sender.sendMessage(MessageManager.colorize("&cYou don't have permission to do this!"));
                    return true;
                }
                
                reloadConfig();
                this.pluginConfig = PluginConfig.fromFileConfiguration(getConfig(), getLogger());
                sender.sendMessage(MessageManager.colorize("&aConfiguration reloaded successfully!"));
                break;
                
            case "info":
                sender.sendMessage(MessageManager.colorize("&3&lArcticDeathChest Info"));
                sender.sendMessage(MessageManager.colorize("&7Version: &f" + getDescription().getVersion()));
                sender.sendMessage(MessageManager.colorize("&7Active Chests: &f" + deathChestManager.getActiveChestCount()));
                sender.sendMessage(MessageManager.colorize("&7Server Version: &f" + VersionUtils.getVersionInfo()));
                sender.sendMessage(MessageManager.colorize("&7Holograms Supported: &f" + VersionUtils.supportsFeature("armor_stands")));
                break;
                
            default:
                sender.sendMessage(MessageManager.colorize("&cUnknown subcommand. Use /arcticdeathchest for help."));
        }
        
        return true;
    }

    @Override
    public void onDisable() {
        isShuttingDown = true;
        
        try {
            if (deathChestManager != null) {
                log.info("Cleaning up death chests...");
                deathChestManager.cleanup();
                deathChestManager = null;
            }
            
            // Clean up managers
            HologramManager.cleanup();
            MessageManager.cleanup();
            
            log.info("ArcticDeathChest has been disabled successfully!");
        } catch (Exception e) {
            log.severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if the plugin is shutting down
     * @return true if the plugin is in shutdown process
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
}

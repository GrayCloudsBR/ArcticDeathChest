package dev.arctic.arcticdeathchest.managers;

import dev.arctic.arcticdeathchest.ArcticDeathChest;
import dev.arctic.arcticdeathchest.utils.VersionUtils;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Effect;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manager for creating, tracking, and cleaning up death chests.
 * Handles falling chest animations, hologram creation, and timed chest destruction.
 */
@Log
public class DeathChestManager {
    private final ArcticDeathChest plugin;
    
    @Getter
    private final ConcurrentHashMap<Location, UUID> deathChests;
    private final ConcurrentHashMap<Location, List<ArmorStand>> chestHolograms;
    private final ConcurrentHashMap<Location, List<Integer>> breakTasks;
    private final ConcurrentHashMap<Location, Integer> fallingChestTasks;

    public DeathChestManager(ArcticDeathChest plugin) {
        this.plugin = plugin;
        this.deathChests = new ConcurrentHashMap<>();
        this.chestHolograms = new ConcurrentHashMap<>();
        this.breakTasks = new ConcurrentHashMap<>();
        this.fallingChestTasks = new ConcurrentHashMap<>();
        
        log.info("DeathChestManager initialized");
    }

    /**
     * Normalize a location to block coordinates for consistent map keys
     */
    private Location normalizeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Create a death chest for a player at the specified location
     * @param player The player who died
     * @param location The death location
     * @param items The items to store in the chest
     * @return true if chest was created successfully, false otherwise
     */
    public boolean createDeathChest(Player player, Location location, List<ItemStack> items) {
        if (player == null || location == null || plugin.isShuttingDown() || plugin.getPluginConfig() == null) {
            return false;
        }
        
        // Filter out null/air items
        List<ItemStack> validItems = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    validItems.add(item);
                }
            }
        }
        
        // Don't create chest if no items
        if (validItems.isEmpty()) {
            log.fine("No valid items to store for " + player.getName());
            return false;
        }
        
        // Get a safe location for the chest
        Location safeLocation = VersionUtils.getSafeChestLocation(location);
        if (safeLocation == null) {
            log.warning("Could not find safe location for death chest");
            return false;
        }
        
        Location normalized = normalizeLocation(safeLocation);
        if (normalized == null) {
            log.warning("Cannot create death chest at invalid location");
            return false;
        }
        
        // Check if there's already a chest at this location
        if (deathChests.containsKey(normalized)) {
            log.warning("Death chest already exists at " + normalized);
            return false;
        }
        
        try {
            // Create falling chest animation if enabled
            if (plugin.getPluginConfig().isFallingChestEnabled()) {
                return createFallingChest(normalized, player, validItems);
            } else {
                return createStaticChest(normalized, player, validItems);
            }
            
        } catch (Exception e) {
            log.warning("Error creating death chest for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            // Clean up partial state
            cleanupChestResources(normalized);
            return false;
        }
    }
    
    /**
     * Create a falling chest animation that lands at the target location
     */
    private boolean createFallingChest(Location location, Player player, List<ItemStack> items) {
        int fallHeight = plugin.getPluginConfig().getFallingChestHeight();
        Location fallLocation = location.clone().add(0.5, fallHeight, 0.5);
        
        // Create falling block
        FallingBlock fallingChest = VersionUtils.spawnFallingBlock(fallLocation, Material.CHEST);
        if (fallingChest == null) {
            // Fallback to static chest if falling block creation failed
            log.warning("Failed to create falling chest, creating static chest instead");
            return createStaticChest(location, player, items);
        }
        
        VersionUtils.setFallingBlockNoDrop(fallingChest);
        
        // Store the player UUID temporarily - actual chest will be registered when it lands
        final UUID playerUUID = player.getUniqueId();
        final String playerName = player.getName();
        final List<ItemStack> itemsCopy = new ArrayList<>(items);
        final Location targetLoc = location.clone();
        
        // Create a task to monitor when the chest lands
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                ticks++;
                
                // Safety check - cancel after 10 seconds (200 ticks)
                if (ticks > 200) {
                    cleanupFallingChestTask(targetLoc);
                    if (fallingChest.isValid()) {
                        fallingChest.remove();
                    }
                    // Create chest at target location
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!plugin.isShuttingDown()) {
                            createStaticChestInternal(targetLoc, playerUUID, playerName, itemsCopy);
                        }
                    });
                    return;
                }
                
                // Check if plugin is shutting down
                if (plugin.isShuttingDown()) {
                    cleanupFallingChestTask(targetLoc);
                    if (fallingChest.isValid()) {
                        fallingChest.remove();
                    }
                    return;
                }
                
                // Check if falling block is gone or landed
                if (!fallingChest.isValid() || fallingChest.isDead() || fallingChest.isOnGround()) {
                    cleanupFallingChestTask(targetLoc);
                    if (fallingChest.isValid()) {
                        fallingChest.remove();
                    }
                    // Create chest at target location
                    createStaticChestInternal(targetLoc, playerUUID, playerName, itemsCopy);
                    return;
                }
                
                // Check if close enough to target
                double distance = fallingChest.getLocation().distance(targetLoc.clone().add(0.5, 0.5, 0.5));
                if (distance < 1.5) {
                    cleanupFallingChestTask(targetLoc);
                    fallingChest.remove();
                    createStaticChestInternal(targetLoc, playerUUID, playerName, itemsCopy);
                }
            }
        }, 1L, 1L);
        
        // Store task ID so we can cancel it if needed
        fallingChestTasks.put(location, task.getTaskId());
        
        return true;
    }
    
    /**
     * Cancel and remove a falling chest monitoring task
     */
    private void cleanupFallingChestTask(Location location) {
        Integer taskId = fallingChestTasks.remove(normalizeLocation(location));
        if (taskId != null) {
            try {
                Bukkit.getScheduler().cancelTask(taskId);
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Create a static chest (wrapper method for external use)
     */
    private boolean createStaticChest(Location location, Player player, List<ItemStack> items) {
        return createStaticChestInternal(location, player.getUniqueId(), player.getName(), items);
    }
    
    /**
     * Internal method to create a static chest with UUID/name instead of Player object
     * (Player object might not be valid when falling chest lands)
     */
    private boolean createStaticChestInternal(Location location, UUID playerUUID, String playerName, List<ItemStack> items) {
        if (plugin.isShuttingDown()) {
            return false;
        }
        
        try {
            Block block = location.getBlock();
            
            // Clear any existing block
            if (block.getType() != Material.AIR) {
                block.setType(Material.AIR);
            }
            
            block.setType(Material.CHEST);
            
            // Verify chest was placed
            if (block.getType() != Material.CHEST) {
                log.warning("Failed to place chest at " + location);
                return false;
            }
            
            Chest chest = (Chest) block.getState();
            
            // Add items to chest
            int itemsStored = 0;
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    try {
                        chest.getInventory().addItem(item);
                        itemsStored++;
                    } catch (Exception e) {
                        log.warning("Failed to add item to death chest: " + e.getMessage());
                    }
                }
            }

            // Store chest location
            Location normalized = normalizeLocation(location);
            deathChests.put(normalized, playerUUID);
            
            int breakTime = plugin.getPluginConfig().getChestBreakTime();

            // Create hologram if enabled and supported
            if (plugin.getPluginConfig().isHologramEnabled() && HologramManager.isSupported()) {
                // Create hologram after a short delay to ensure chest is fully created
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!plugin.isShuttingDown() && deathChests.containsKey(normalized)) {
                        try {
                            // Get the player if online, otherwise create hologram with just name
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null) {
                                List<ArmorStand> hologram = HologramManager.createHologram(normalized, player, breakTime);
                                if (hologram != null && !hologram.isEmpty()) {
                                    chestHolograms.put(normalized, hologram);
                                }
                            } else {
                                // Player is offline, skip hologram or create with minimal info
                                log.fine("Player offline, skipping hologram for " + playerName);
                            }
                        } catch (Exception e) {
                            log.warning("Failed to create hologram for death chest: " + e.getMessage());
                        }
                    }
                }, 2L);
            }
            
            // Schedule chest break
            scheduleChestBreak(normalized, breakTime);
            
            log.info("Created death chest for " + playerName + " at " + 
                     location.getWorld().getName() + " " + location.getBlockX() + 
                     "," + location.getBlockY() + "," + location.getBlockZ() +
                     " (" + itemsStored + " items)");
            
            return true;
                     
        } catch (Exception e) {
            log.warning("Error creating static chest: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Schedule the automatic chest break after the configured time
     */
    private void scheduleChestBreak(Location location, int breakTime) {
        if (location == null || breakTime <= 0 || plugin.isShuttingDown()) {
            return;
        }
        
        List<Integer> taskIds = new CopyOnWriteArrayList<>();
        
        try {
            // Update timer every second if holograms are enabled
            if (plugin.getPluginConfig().isHologramEnabled() && HologramManager.isSupported()) {
                for (int i = breakTime - 1; i > 0; i--) {
                    final int seconds = i;
                    final int delay = (breakTime - i) * 20;
                    
                    int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!plugin.isShuttingDown() && deathChests.containsKey(location)) {
                            try {
                                List<ArmorStand> hologram = chestHolograms.get(location);
                                if (hologram != null && !hologram.isEmpty()) {
                                    HologramManager.updateTimer(hologram, seconds);
                                }
                            } catch (Exception e) {
                                log.warning("Error updating hologram timer: " + e.getMessage());
                            }
                        }
                    }, delay).getTaskId();
                    taskIds.add(taskId);
                }
            }

            // Schedule the final break task
            int finalTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isShuttingDown() && deathChests.containsKey(location)) {
                    breakChest(location);
                }
            }, breakTime * 20L).getTaskId();
            taskIds.add(finalTaskId);
            
            breakTasks.put(location, taskIds);
        } catch (Exception e) {
            log.warning("Error scheduling chest break: " + e.getMessage());
            // Clean up any tasks that were created
            for (Integer taskId : taskIds) {
                try {
                    Bukkit.getScheduler().cancelTask(taskId);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Cancel all scheduled break tasks for a chest
     */
    public void cancelBreakTask(Location location) {
        Location normalized = normalizeLocation(location);
        if (normalized == null) {
            return;
        }
        
        List<Integer> taskIds = breakTasks.remove(normalized);
        if (taskIds != null) {
            for (Integer taskId : taskIds) {
                try {
                    if (taskId != null) {
                        Bukkit.getScheduler().cancelTask(taskId);
                    }
                } catch (Exception e) {
                    log.warning("Error cancelling break task: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Break a death chest and drop its contents
     */
    public void breakChest(Location location) {
        Location normalized = normalizeLocation(location);
        if (normalized == null || !deathChests.containsKey(normalized)) {
            return;
        }
        
        try {
            Block block = normalized.getBlock();
            if (block.getType() != Material.CHEST) {
                // Chest was already broken somehow, just clean up tracking
                cleanupChestResources(normalized);
                return;
            }
            
            Chest chest = (Chest) block.getState();
            
            // 1. Get and clear items
            ItemStack[] items = chest.getInventory().getContents();
            chest.getInventory().clear();
            
            // 2. Clean up resources first (tasks, holograms, tracking)
            cleanupChestResources(normalized);
            
            // 3. Break chest with visual effect
            try {
                block.getWorld().playEffect(block.getLocation(), Effect.SMOKE, 0);
            } catch (Exception e) {
                // Effect might not work on all versions
                log.fine("Could not play smoke effect: " + e.getMessage());
            }
            
            block.setType(Material.AIR);
            
            // 4. Drop items naturally
            int dropped = 0;
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    try {
                        Item droppedItem = normalized.getWorld().dropItemNaturally(
                            normalized.clone().add(0.5, 0.5, 0.5), item);
                        // Reduce velocity for a nicer effect
                        droppedItem.setVelocity(droppedItem.getVelocity().multiply(0.3));
                        dropped++;
                    } catch (Exception e) {
                        log.warning("Error dropping item from death chest: " + e.getMessage());
                    }
                }
            }
            
            // 5. Play break sound
            playBreakSound(normalized);
            
            // 6. Send break message
            MessageManager.sendBreakMessage();
            
            log.info("Death chest broken at " + normalized.getWorld().getName() + 
                     " " + normalized.getBlockX() + "," + normalized.getBlockY() + 
                     "," + normalized.getBlockZ() + " (" + dropped + " items dropped)");
                     
        } catch (Exception e) {
            log.warning("Error breaking death chest: " + e.getMessage());
            e.printStackTrace();
            // Ensure cleanup happens even if breaking fails
            cleanupChestResources(normalized);
        }
    }
    
    /**
     * Clean up all resources associated with a death chest
     */
    private void cleanupChestResources(Location location) {
        if (location == null) {
            return;
        }
        
        try {
            // Cancel any break tasks
            cancelBreakTask(location);
            
            // Cancel any falling chest task
            cleanupFallingChestTask(location);
            
            // Remove and clean up hologram
            List<ArmorStand> hologram = chestHolograms.remove(location);
            if (hologram != null) {
                HologramManager.removeHologram(hologram);
                HologramManager.untrackHologram(location);
            }
            
            // Remove from tracking
            deathChests.remove(location);
            
        } catch (Exception e) {
            log.warning("Error during chest resource cleanup: " + e.getMessage());
        }
    }

    /**
     * Play the chest break sound
     */
    private void playBreakSound(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        
        try {
            Sound sound = VersionUtils.getBlockBreakSound();
            if (sound != null) {
                location.getWorld().playSound(location, sound, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            log.fine("Error playing break sound: " + e.getMessage());
        }
    }

    /**
     * Clean up all death chests (called on plugin disable)
     */
    public void cleanup() {
        try {
            log.info("Cleaning up " + deathChests.size() + " death chests...");
            
            // Cancel all falling chest tasks first
            for (Integer taskId : fallingChestTasks.values()) {
                try {
                    Bukkit.getScheduler().cancelTask(taskId);
                } catch (Exception ignored) {}
            }
            fallingChestTasks.clear();
            
            // Create a copy of locations to avoid concurrent modification
            List<Location> locations = new ArrayList<>(deathChests.keySet());
            
            for (Location loc : locations) {
                try {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.CHEST) {
                        breakChest(loc);
                    } else {
                        // Just clean up resources if chest is already gone
                        cleanupChestResources(loc);
                    }
                } catch (Exception e) {
                    log.warning("Error cleaning up death chest at " + loc + ": " + e.getMessage());
                    // Force cleanup even if breaking fails
                    cleanupChestResources(loc);
                }
            }
            
            // Force clear all collections
            deathChests.clear();
            chestHolograms.clear();
            breakTasks.clear();
            fallingChestTasks.clear();
            
            log.info("Death chest cleanup completed.");
            
        } catch (Exception e) {
            log.severe("Error during death chest manager cleanup: " + e.getMessage());
            e.printStackTrace();
            
            // Force clear collections as last resort
            try {
                deathChests.clear();
                chestHolograms.clear();
                breakTasks.clear();
                fallingChestTasks.clear();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Check if a location contains a death chest
     */
    public boolean isDeathChest(Location location) {
        Location normalized = normalizeLocation(location);
        return normalized != null && deathChests.containsKey(normalized);
    }
    
    /**
     * Get the number of active death chests
     * @return number of active death chests
     */
    public int getActiveChestCount() {
        return deathChests.size();
    }
    
    /**
     * Get the owner of a death chest
     * @param location the location of the chest
     * @return the UUID of the owner, or null if not a death chest
     */
    public UUID getChestOwner(Location location) {
        Location normalized = normalizeLocation(location);
        return normalized != null ? deathChests.get(normalized) : null;
    }
}

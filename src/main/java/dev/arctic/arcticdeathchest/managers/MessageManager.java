package dev.arctic.arcticdeathchest.managers;

import dev.arctic.arcticdeathchest.ArcticDeathChest;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Manager for handling all plugin messages.
 * Supports color codes and placeholder replacement.
 */
@Log
public class MessageManager {
    private static ArcticDeathChest plugin;

    public static void initialize(ArcticDeathChest main) {
        plugin = main;
        log.info("MessageManager initialized");
    }

    /**
     * Get the configured prefix, with color codes translated
     */
    private static String getPrefix() {
        if (plugin == null || plugin.getPluginConfig() == null) {
            return ChatColor.WHITE + "[" + ChatColor.DARK_AQUA + "AntryDeathLoot" + ChatColor.WHITE + "] ";
        }
        return colorize(plugin.getPluginConfig().getPrefix());
    }

    /**
     * Send the death chest creation message
     */
    public static void sendDeathChestMessage(Player player) {
        if (plugin == null || player == null || plugin.getPluginConfig() == null) {
            return;
        }
        
        try {
            if (plugin.getPluginConfig().isAnnounceDeathChest()) {
                String message = plugin.getPluginConfig().getDeathChestMessage()
                    .replace("%player%", player.getName())
                    .replace("%time%", String.valueOf(plugin.getPluginConfig().getChestBreakTime()));
                broadcast(message);
            }
        } catch (Exception e) {
            log.warning("Error sending death chest message: " + e.getMessage());
        }
    }

    /**
     * Send the chest break message
     */
    public static void sendBreakMessage() {
        if (plugin == null || plugin.getPluginConfig() == null) {
            return;
        }
        
        try {
            String breakMessage = plugin.getPluginConfig().getChestBreakMessage();
            if (breakMessage != null && !breakMessage.isEmpty()) {
                broadcast(breakMessage);
            }
        } catch (Exception e) {
            log.warning("Error sending break message: " + e.getMessage());
        }
    }

    /**
     * Broadcast a message to all players with the plugin prefix
     */
    private static void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        try {
            message = getPrefix() + colorize(message);
            Bukkit.broadcastMessage(message);
        } catch (Exception e) {
            log.warning("Error broadcasting message: " + e.getMessage());
        }
    }
    
    /**
     * Send a message to a specific player
     * @param player The player to send to
     * @param message The message to send (supports & color codes)
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        
        try {
            player.sendMessage(getPrefix() + colorize(message));
        } catch (Exception e) {
            log.warning("Error sending message to player: " + e.getMessage());
        }
    }
    
    /**
     * Send a message to a command sender (player or console)
     * @param sender The command sender
     * @param message The message to send (supports & color codes)
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        
        try {
            sender.sendMessage(getPrefix() + colorize(message));
        } catch (Exception e) {
            log.warning("Error sending message to sender: " + e.getMessage());
        }
    }
    
    /**
     * Translate color codes in a message
     * @param message The message with & color codes
     * @return The message with translated color codes
     */
    public static String colorize(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Clean up the message manager
     */
    public static void cleanup() {
        log.info("MessageManager cleanup completed");
        plugin = null;
    }
}

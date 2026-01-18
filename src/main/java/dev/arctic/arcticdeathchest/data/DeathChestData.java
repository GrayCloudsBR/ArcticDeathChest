package dev.arctic.arcticdeathchest.data;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Immutable data class representing a death chest
 */
@Data
@Builder
public class DeathChestData {
    @NonNull
    private final UUID ownerUuid;
    
    @NonNull
    private final String ownerName;
    
    @NonNull
    private final Location location;
    
    @NonNull
    private final List<ItemStack> items;
    
    @NonNull
    private final LocalDateTime createdAt;
    
    private final int breakTimeSeconds;
    
    private final List<ArmorStand> hologram;
    
    private final List<Integer> taskIds;
    
    /**
     * Check if this death chest has expired
     * @return true if the chest should be broken
     */
    public boolean isExpired() {
        return createdAt.plusSeconds(breakTimeSeconds).isBefore(LocalDateTime.now());
    }
    
    /**
     * Get remaining seconds until break
     * @return seconds remaining, or 0 if expired
     */
    public long getRemainingSeconds() {
        long remaining = breakTimeSeconds - java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        return Math.max(0, remaining);
    }
} 
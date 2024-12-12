package org.picklecraft.prypurity.picklerandomtp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class PickleRandomTP extends JavaPlugin implements CommandExecutor {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private FileConfiguration messagesConfig;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveDefaultMessages();
        this.getCommand("rtp").setExecutor(this);
        this.getCommand("prtpmind").setExecutor(this);
        this.getCommand("prtpmaxd").setExecutor(this);
        this.getCommand("prtpsetcenter").setExecutor(this);
    }

    private void saveDefaultMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.getString(key);
        if (message == null) {
            return messagesConfig.getString("Error");
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rtp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("only_players", null));
                return true;
            }

            if (!player.hasPermission("picklerandomtp.use")) {
                player.sendMessage(getMessage("no_permission", null));
                return true;
            }

            FileConfiguration config = this.getConfig();
            int minDistance = config.getInt("min-distance");
            int maxDistance = config.getInt("max-distance");
            int cooldownTime = config.getInt("cooldown");
            boolean checkClaims = config.getBoolean("check-claims");

            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();

            if (!player.hasPermission("picklerandomtp.bypass") && cooldowns.containsKey(playerUUID)) {
                long lastUsed = cooldowns.get(playerUUID);
                if (currentTime - lastUsed < cooldownTime * 1000L) {
                    player.sendMessage(getMessage("cooldown", null));
                    return true;
                }
            }
            World world = player.getWorld();
            for (String whitelistWorld : this.getConfig().getStringList("whitelist")) {
                if (world.getName().equals(whitelistWorld)) {
                    Location center = new Location(player.getWorld(), config.getDouble("center.x"), config.getDouble("center.y"), config.getDouble("center.z"));

                    Location randomLocation = getRandomLocation(center, minDistance, maxDistance, checkClaims);
                    randomLocation.setY(randomLocation.getY() + 1);
                    player.teleport(randomLocation);
                    player.sendMessage(getMessage("teleported", null));

                    cooldowns.put(playerUUID, currentTime);
                    return true;
                }
            } player.sendMessage(getMessage("Not_Whitelisted", null));
            return false;

        } else if (command.getName().equalsIgnoreCase("prtpmind")) {
            if (!sender.hasPermission("picklerandomtp.admin")) {
                sender.sendMessage(getMessage("no_permission", null));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(getMessage("usage", null));
                return true;
            }

            int minDistance;
            try {
                minDistance = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("invalid_number", null));
                return true;
            }

            this.getConfig().set("min-distance", minDistance);
            this.saveConfig();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("distance", String.valueOf(minDistance));
            sender.sendMessage(getMessage("min_distance_set", placeholders));
            return true;
        } else if (command.getName().equalsIgnoreCase("prtpmaxd")) {
            if (!sender.hasPermission("picklerandomtp.admin")) {
                sender.sendMessage(getMessage("no_permission", null));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(getMessage("usage", null));
                return true;
            }

            int maxDistance;
            try {
                maxDistance = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("invalid_number", null));
                return true;
            }

            this.getConfig().set("max-distance", maxDistance);
            this.saveConfig();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("distance", String.valueOf(maxDistance));
            sender.sendMessage(getMessage("max_distance_set", placeholders));
            return true;
        } else if (command.getName().equalsIgnoreCase("prtpsetcenter")) {
            if (!sender.hasPermission("picklerandomtp.admin")) {
                sender.sendMessage(getMessage("no_permission", null));
                return true;
            }

            if (args.length != 3) {
                sender.sendMessage(getMessage("usage", null));
                return true;
            }

            double x, y, z;
            try {
                x = Double.parseDouble(args[0]);
                y = Double.parseDouble(args[1]);
                z = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(getMessage("invalid_number", null));
                return true;
            }

            this.getConfig().set("center.x", x);
            this.getConfig().set("center.y", y);
            this.getConfig().set("center.z", z);
            this.saveConfig();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("x", String.valueOf(x));
            placeholders.put("y", String.valueOf(y));
            placeholders.put("z", String.valueOf(z));
            sender.sendMessage(getMessage("center_set", placeholders));
            return true;
        }

        return false;
    }

    private Location getRandomLocation(Location center, int minDistance, int maxDistance, boolean checkClaims) {
        Random random = new Random();
        Location randomLocation;
        do {
            int x = center.getBlockX() + (random.nextInt((maxDistance - minDistance) * 2) - maxDistance);
            int z = center.getBlockZ() + (random.nextInt((maxDistance - minDistance) * 2) - maxDistance);
            int y = center.getWorld().getHighestBlockYAt(x, z);
            randomLocation = new Location(center.getWorld(), x, y, z);
        } while (checkClaims && isClaimed(randomLocation) || isUnsafe(randomLocation));
        return randomLocation;
    }

    private boolean isClaimed(Location location) {
        // Check WorldGuard regions
        WorldGuardPlugin worldGuard = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null) {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager != null) {
                ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
                if (!regions.getRegions().isEmpty()) {
                    return true;
                }
            }
        }

        // Check GriefPrevention claims
        GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention != null) {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            return claim != null;
        }
        return false;
    }

    private boolean isUnsafe(Location location) {
        Material blockType = location.getBlock().getType();
        List<String> unsafeMaterials = this.getConfig().getStringList("unsafe-materials");
        return unsafeMaterials.contains(blockType.toString());
    }

    @Override
    public void onDisable() {
        // Save the plugin's configuration & clear the cooldowns to free memory.
        this.saveConfig();
        cooldowns.clear();
    }
}


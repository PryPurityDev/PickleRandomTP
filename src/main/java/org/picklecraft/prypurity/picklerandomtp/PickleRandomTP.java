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
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class PickleRandomTP extends JavaPlugin implements CommandExecutor {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private FileConfiguration messagesConfig;

    private static final String ERROR_KEY = "Error";
    private static final String NO_PERMISSION_KEY = "no_permission";
    private static final String USAGE_KEY = "usage";
    private static final String INVALID_NUMBER_KEY = "invalid_number";
    private static final String NOT_WHITELISTED_KEY = "Not_Whitelisted";

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
        String message = messagesConfig.getString(key, messagesConfig.getString(ERROR_KEY));
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
            return handleRTPCommand(sender);
        } else if (command.getName().equalsIgnoreCase("prtpmind")) {
            return handleMinDistanceCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("prtpmaxd")) {
            return handleMaxDistanceCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("prtpsetcenter")) {
            return handleSetCenterCommand(sender, args);
        }
        return false;
    }

    private boolean handleRTPCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("only_players", null));
            return true;
        }

        if (!player.hasPermission("picklerandomtp.use")) {
            player.sendMessage(getMessage(NO_PERMISSION_KEY, null));
            return true;
        }

        FileConfiguration config = this.getConfig();
        int minDistance = config.getInt("min-distance");
        int maxDistance = config.getInt("max-distance");
        int cooldownTime = config.getInt("cooldown");
        boolean countdownEnabled = config.getBoolean("countdown.enabled");
        int countdownTime = config.getInt("countdown.time");
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

                if (countdownEnabled) {
                    player.sendMessage(getMessage("countdown_start", null).replace("{time}", String.valueOf(countdownTime)));
                    Location beforeteleport = player.getLocation();

                    new BukkitRunnable() {
                        int countdown = countdownTime;

                        @Override
                        public void run() {
                            //Check for player movement
                            if (!player.getLocation().getBlock().equals(beforeteleport.getBlock())) {
                                player.sendMessage(getMessage("countdown_cancelled", null));
                                cancel();
                                return;
                            }
                            if (countdown > 0) {
                                player.sendMessage(getMessage("countdown_tick", null).replace("{time}", String.valueOf(countdown)));
                                countdown--;
                            } else {
                                player.teleport(randomLocation);
                                player.sendMessage(getMessage("teleported", null));
                                cooldowns.put(playerUUID, currentTime);
                                cancel();
                            }
                        }
                    }.runTaskTimer(this, 0, 20); //20 ticks is 1 second, moron.
                } else {
                    player.teleport(randomLocation);
                    player.sendMessage(getMessage("teleported", null));
                    cooldowns.put(playerUUID, currentTime);
                }
                return true;
            }
        }
        player.sendMessage(getMessage(NOT_WHITELISTED_KEY, null));
        return false;
    }

    private boolean handleMinDistanceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("picklerandomtp.admin")) {
            sender.sendMessage(getMessage(NO_PERMISSION_KEY, null));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(getMessage(USAGE_KEY, null));
            return true;
        }

        int minDistance;
        try {
            minDistance = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage(INVALID_NUMBER_KEY, null));
            return true;
        }

        updateConfigValue("min-distance", minDistance, sender, "min_distance_set");
        return true;
    }

    private boolean handleMaxDistanceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("picklerandomtp.admin")) {
            sender.sendMessage(getMessage(NO_PERMISSION_KEY, null));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(getMessage(USAGE_KEY, null));
            return true;
        }

        int maxDistance;
        try {
            maxDistance = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage(INVALID_NUMBER_KEY, null));
            return true;
        }

        updateConfigValue("max-distance", maxDistance, sender, "max_distance_set");
        return true;
    }

    private boolean handleSetCenterCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("picklerandomtp.admin")) {
            sender.sendMessage(getMessage(NO_PERMISSION_KEY, null));
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(getMessage(USAGE_KEY, null));
            return true;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage(INVALID_NUMBER_KEY, null));
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

    private void updateConfigValue(String path, int value, CommandSender sender, String messageKey) {
        this.getConfig().set(path, value);
        this.saveConfig();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("distance", String.valueOf(value));
        sender.sendMessage(getMessage(messageKey, placeholders));
    }

    private Location getRandomLocation(Location center, int minDistance, int maxDistance, boolean checkClaims) {
        Random random = new Random();
        Location randomLocation;
        int attempts = 0;
        do {
            int x = center.getBlockX() + (random.nextInt((maxDistance - minDistance) * 2) - maxDistance);
            int z = center.getBlockZ() + (random.nextInt((maxDistance - minDistance) * 2) - maxDistance);
            int y = center.getWorld().getHighestBlockYAt(x, z);
            randomLocation = new Location(center.getWorld(), x, y, z);
            attempts++;
            if (attempts > 100) {
                return center; // Fallback to center if too many attempts
            }
        } while (checkClaims && (isClaimed(randomLocation) || isUnsafe(randomLocation)));
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
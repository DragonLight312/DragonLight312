package ir.emostaff.emostaff;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EmoStaff extends JavaPlugin {

  private FileConfiguration config;
  private File storageFile;
  private FileConfiguration storage;

  private String prefix;
  private Scoreboard staffBoard;
  private Objective staffObj;
  private Team staffTeam;
  private final Map<UUID, Long> cooldowns = new HashMap<>();
  private final Map<UUID, Long> adminCooldowns = new HashMap<>();

  @Override
  public void onEnable() {
    saveDefaultConfig();
    config = getConfig();
    setupStorage();
    setupScoreboard();
    prefix = color(config.getString("Messages.Prefix", "&2&lEmo&aStaff&7 » "));
    getLogger().info(prefix + "Enabled v" + getDescription().getVersion());
    getLogger().info("DEVELOPED BY CAMELIAAM");
  }

  @Override
  public void onDisable() {
    getLogger().info("DEVELOPED BY CAMELIAAM");
    getLogger().info(prefix + "Disabled");
  }

  private void setupStorage() {
    storageFile = new File(getDataFolder(), "storage.yml");
    if (!storageFile.exists()) {
      try {
        storageFile.createNewFile();
      } catch (IOException e) {
        getLogger().severe("Cannot create storage.yml!");
      }
    }
    storage = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(storageFile);
    if (!storage.contains("StaffMode.Trues")) storage.createSection("StaffMode.Trues");
    if (!storage.contains("Admins")) storage.createSection("Admins");
    saveStorage();
  }

  private void saveStorage() {
    try {
      storage.save(storageFile);
    } catch (IOException e) {
      getLogger().severe("Failed to save storage.yml!");
    }
  }

  private void setupScoreboard() {
    staffBoard = Bukkit.getScoreboardManager().getNewScoreboard();
    staffObj = staffBoard.registerNewObjective("staffmode", "dummy", ChatColor.RED + "Staff Mode");
    staffObj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    staffTeam = staffBoard.registerNewTeam("staff");
    staffTeam.setPrefix(color("&c[STAFF] "));
  }

  private String color(String text) {
    return ChatColor.translateAlternateColorCodes('&', text);
  }

  private String msg(String path) {
    return color(config.getString("Messages." + path, "ERR: " + path)).replace("%prefix%", prefix);
  }

  private String msg(String path, String... replacements) {
    String message = color(config.getString("Messages." + path, "ERR: " + path));
    for (int i = 0; i < replacements.length; i += 2) {
      message = message.replace(replacements[i], replacements[i + 1]);
    }
    return message.replace("%prefix%", prefix);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

    if (cmd.getName().equalsIgnoreCase("emostaff")) {
      if (!sender.hasPermission("emostaff.use")) {
        sender.sendMessage(msg("No-Permission"));
        return true;
      }

      if (args.length == 0) {
        // Help display
        sender.sendMessage(color("&8&l&m---------------"));
        sender.sendMessage(color("&a/admin"));
        sender.sendMessage(color("&a/gui"));
        sender.sendMessage(color("&a/test"));
        sender.sendMessage(color("&8&l&m---------------"));
        return true;
      }

      return handleEmoStaffAdmin(sender, args);
    }

    if (cmd.getName().equalsIgnoreCase("staffmode")) {
      if (!(sender instanceof Player p)) {
        sender.sendMessage(msg("Players-Only"));
        return true;
      }

      if (!p.hasPermission("emostaff.usemode")) {
        p.sendMessage(msg("No-Permission"));
        return true;
      }

      if (config.getBoolean("Admin-CMD", true) && !storage.getBoolean("Admins." + p.getUniqueId(), false)) {
        p.sendMessage(msg("Admin-Denied"));
        return true;
      }

      long now = System.currentTimeMillis() / 1000;
      long last = cooldowns.getOrDefault(p.getUniqueId(), 0L);
      int cooldown = config.getInt("Cooldown-Seconds", 5);
      if (now - last < cooldown) {
        p.sendMessage(msg("On-Cooldown", "%seconds%", String.valueOf(cooldown - (now - last))));
        return true;
      }

      toggleStaffMode(p, !isStaffModeActive(p));
      p.sendMessage(isStaffModeActive(p) ? msg("StaffMode-Enabled") : msg("StaffMode-Disabled"));
      cooldowns.put(p.getUniqueId(), now);
      return true;
    }

    return false;
  }

  private boolean handleEmoStaffAdmin(CommandSender sender, String[] args) {
    if (!sender.hasPermission("emostaff.admin")) {
      sender.sendMessage(msg("No-Permission"));
      return true;
    }

    if (args[0].equalsIgnoreCase("admin") && args.length >= 3) {
      Player target = Bukkit.getPlayer(args[2]);
      if (target == null) {
        sender.sendMessage("Player not found!");
        return true;
      }

      boolean value = Boolean.parseBoolean(args[3]);
      String type = args[1].toLowerCase();
      switch (type) {
        case "permission" -> {
          storage.set("Admins." + target.getUniqueId(), value);
          saveStorage();
          sender.sendMessage(value ? msg("Admin.Permission-Granted", "%player%", target.getName())
            : msg("Admin.Permission-Revoked", "%player%", target.getName()));
        }
        case "staffmode" -> {
          toggleStaffMode(target, value);
          sender.sendMessage(value ? msg("Admin.StaffMode-On", "%player%", target.getName())
            : msg("Admin.StaffMode-Off", "%player%", target.getName()));
        }
      }
      return true;
    }

    if (args[0].equalsIgnoreCase("gui") && sender instanceof Player player) {
      openStaffGUI(player);
      return true;
    }

    sender.sendMessage("Unknown argument.");
    return true;
  }

  private boolean isStaffModeActive(Player p) {
    return storage.contains("StaffMode.Trues." + p.getUniqueId());
  }

  private void toggleStaffMode(Player p, boolean enable) {
    String path = "StaffMode.Trues." + p.getUniqueId();
    if (enable) {
      storage.set(path + ".User", p.getName());
      storage.set(path + ".Uuid", p.getUniqueId().toString());
      saveStorage();

      p.setAllowFlight(true);
      p.setFlying(true);
      p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));

      p.setPlayerListName(color("&c[STAFF] ") + p.getName());
      staffTeam.addEntry(p.getName());
      p.setScoreboard(staffBoard);
    } else {
      storage.set(path, null);
      saveStorage();

      p.setAllowFlight(false);
      p.setFlying(false);
      p.removePotionEffect(PotionEffectType.NIGHT_VISION);

      p.setPlayerListName(p.getName());
      staffTeam.removeEntry(p.getName());
      p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
  }

  private void openStaffGUI(Player player) {
    String title = color(config.getString("GUI.Title", "&8» &cStaff Members"));
    int size = config.getInt("GUI.Size", 27);
    Inventory inv = Bukkit.createInventory(null, size, title);

    Material glassMat = Material.getMaterial(config.getString("GUI.Item.Material", "BLACK_STAINED_GLASS_PANE"));
    String glassName = config.getString("GUI.Item.Name", " ");
    List<String> glassLore = config.getStringList("GUI.Item.Lore");
    boolean glassGlow = config.getBoolean("GUI.Item.Glow", false);

    ItemStack glass = new ItemStack(glassMat);
    ItemMeta meta = glass.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(color(glassName));
      List<String> coloredLore = new ArrayList<>();
      for (String line : glassLore) coloredLore.add(color(line));
      meta.setLore(coloredLore);
      if (glassGlow) meta.addEnchant(Enchantment.DURABILITY, 1, false);
    }
    glass.setItemMeta(meta);

    for (int i = 0; i < size; i++) inv.setItem(i, glass);

    if (storage.contains("StaffMode.Trues")) {
      for (String uuidStr : storage.getConfigurationSection("StaffMode.Trues").getKeys(false)) {
        try {
          UUID uuid = UUID.fromString(uuidStr);
          String name = storage.getString("StaffMode.Trues." + uuidStr + ".User", "Unknown");

          ItemStack head = new ItemStack(Material.PLAYER_HEAD);
          SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
          if (skullMeta != null) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            skullMeta.setDisplayName(color(config.getString("GUI.Item.Name", "&e%player%").replace("%player%", name)));
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList("GUI.Item.Lore")) {
              lore.add(color(line.replace("%player%", name).replace("%uuid%", uuidStr)));
            }
            skullMeta.setLore(lore);
            if (config.getBoolean("GUI.Item.Glow", false)) {
              skullMeta.addEnchant(Enchantment.DURABILITY, 1, false);
              skullMeta.setUnbreakable(true);
            }
            head.setItemMeta(skullMeta);
          }
          inv.addItem(head);
        } catch (IllegalArgumentException ignored) {}
      }
    }

    player.openInventory(inv);
  }
}

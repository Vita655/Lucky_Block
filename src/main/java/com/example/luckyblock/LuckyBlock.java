package com.example.luckyblock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Lucky Block (Mario style). Craft it with gold ingots around an oak or dark oak log.
 * Break a placed Lucky Block for a random good or bad surprise.
 */
public class LuckyBlock extends JavaPlugin implements Listener {

    private static final String NAME = ChatColor.GOLD + "" + ChatColor.BOLD + "Lucky Block";

    private final Set<String> blocks = new HashSet<String>();
    private final Random random = new Random();
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "blocks.yml");
        blocks.addAll(YamlConfiguration.loadConfiguration(dataFile).getStringList("blocks"));
        addRecipes();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveBlocks();
    }

    // ---------- item + recipe ----------

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(NAME);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Place it, then break it", ChatColor.GRAY + "for a surprise!"));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isLucky(ItemStack item) {
        return item != null
                && item.getType() == Material.GOLD_BLOCK
                && item.hasItemMeta()
                && NAME.equals(item.getItemMeta().getDisplayName());
    }

    @SuppressWarnings("deprecation")
    private void addRecipes() {
        // Oak log in the middle
        ShapedRecipe oak = new ShapedRecipe(createItem());
        oak.shape(" G ", "GLG", " G ");
        oak.setIngredient('G', Material.GOLD_INGOT);
        oak.setIngredient('L', Material.LOG, 0);
        Bukkit.addRecipe(oak);

        // Dark oak log in the middle
        ShapedRecipe dark = new ShapedRecipe(createItem());
        dark.shape(" G ", "GLG", " G ");
        dark.setIngredient('G', Material.GOLD_INGOT);
        dark.setIngredient('L', Material.LOG_2, 1);
        Bukkit.addRecipe(dark);
    }

    // ---------- tracking placed blocks ----------

    private String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private void saveBlocks() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("blocks", new ArrayList<String>(blocks));
        try {
            y.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save blocks.yml: " + e.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isLucky(e.getItemInHand())) {
            blocks.add(key(e.getBlock().getLocation()));
            saveBlocks();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.GOLD_BLOCK) return;
        if (!blocks.remove(key(b.getLocation()))) return;

        e.setCancelled(true);       // no normal gold block drop
        b.setType(Material.AIR);
        saveBlocks();
        surprise(e.getPlayer(), b.getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        // Lucky Blocks survive explosions
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType() == Material.GOLD_BLOCK && blocks.contains(key(b.getLocation()))) {
                it.remove();
            }
        }
    }

    // ---------- surprises ----------

    private void surprise(Player p, Location loc) {
        double goodChance = getConfig().getDouble("good-chance", 0.5);
        if (random.nextDouble() < goodChance) {
            good(p, loc);
        } else {
            bad(p, loc);
        }
    }

    private void good(Player p, Location loc) {
        World w = loc.getWorld();
        String good = ChatColor.GREEN + "Lucky! " + ChatColor.WHITE;
        switch (random.nextInt(6)) {
            case 0:
                w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 2 + random.nextInt(4)));
                p.sendMessage(good + "Diamonds!");
                break;
            case 1:
                w.dropItemNaturally(loc, new ItemStack(Material.GOLDEN_APPLE, 1 + random.nextInt(3)));
                p.sendMessage(good + "Golden apples!");
                break;
            case 2:
                w.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, 5 + random.nextInt(8)));
                p.sendMessage(good + "Iron!");
                break;
            case 3:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 1));
                p.sendMessage(good + "Speed boost!");
                break;
            case 4:
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 15, 1));
                p.sendMessage(good + "Fully healed!");
                break;
            default:
                p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 30, 0));
                p.sendMessage(good + "Strength!");
                break;
        }
    }

    private void bad(Player p, Location loc) {
        World w = loc.getWorld();
        String bad = ChatColor.RED + "Unlucky! " + ChatColor.WHITE;
        switch (random.nextInt(6)) {
            case 0:
                for (int i = 0; i < 3; i++) w.spawnEntity(loc, EntityType.ZOMBIE);
                p.sendMessage(bad + "Zombies!");
                break;
            case 1:
                w.strikeLightning(p.getLocation());
                p.sendMessage(bad + "Lightning!");
                break;
            case 2:
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 8, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20 * 10, 0));
                p.sendMessage(bad + "You can't see straight!");
                break;
            case 3:
                // power 2.5, no fire, does not break blocks
                w.createExplosion(loc.getX(), loc.getY(), loc.getZ(), 2.5f, false, false);
                p.sendMessage(bad + "Boom!");
                break;
            case 4:
                w.spawnEntity(loc, EntityType.CREEPER);
                p.sendMessage(bad + "A creeper!");
                break;
            default:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 15, 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20 * 15, 1));
                p.sendMessage(bad + "Slow and hungry...");
                break;
        }
    }

    // ---------- /luckyblock give [player] ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            Player target = null;
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
            } else if (sender instanceof Player) {
                target = (Player) sender;
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found. Use /luckyblock give <player>");
                return true;
            }
            target.getInventory().addItem(createItem());
            sender.sendMessage(ChatColor.GREEN + "Gave a Lucky Block to " + target.getName() + ".");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /luckyblock give [player]");
        return true;
    }
}

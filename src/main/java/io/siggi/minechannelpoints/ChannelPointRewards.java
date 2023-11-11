package io.siggi.minechannelpoints;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.siggi.minechannelpoints.MineChannelPoints.announce;
import static io.siggi.minechannelpoints.MineChannelPoints.give;

public class ChannelPointRewards {
    private ChannelPointRewards() {
    }

    private static final Map<String, ChannelPointReward> rewards = new HashMap<>();
    private static final Map<String, ChannelPointReward> immutableRewards = Collections.unmodifiableMap(rewards);

    private static void addReward(ChannelPointReward reward) {
        rewards.put(reward.id(), reward);
    }

    static {
        addReward(new ChannelPointReward(
                "spawn_zombie",
                "Spawn Zombie",
                "With full diamond! XD",
                false,
                500,
                (player, redeemer, text) -> {
                    if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                        announce(redeemer + " tried to redeem Spawn Zombie on " + player.getName() + ", but was rejected since the player is in the nether.");
                        return false;
                    }
                    Location location = player.getLocation();
                    Zombie zombie = (Zombie) player.getWorld().spawnEntity(location, EntityType.ZOMBIE);
                    EntityEquipment equipment = zombie.getEquipment();
                    equipment.setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1), true);
                    equipment.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE, 1), true);
                    equipment.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS, 1), true);
                    equipment.setBoots(new ItemStack(Material.DIAMOND_BOOTS, 1), true);
                    equipment.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD, 1), true);
                    equipment.setItemInOffHand(new ItemStack(Material.BAKED_POTATO, 4), true);
                    equipment.setHelmetDropChance(0.15f);
                    equipment.setChestplateDropChance(0.15f);
                    equipment.setLeggingsDropChance(0.15f);
                    equipment.setBootsDropChance(0.15f);
                    equipment.setItemInMainHandDropChance(0.15f);
                    equipment.setItemInOffHandDropChance(1.0f);
                    zombie.setTarget(player);
                    announce(redeemer + " gifted " + player.getName() + " a zombie!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_cat",
                "Spawn Cat",
                "Enter a name for the cat:",
                true,
                2500,
                (player, redeemer, text) -> {
                    if (text.contains("$")) {
                        announce(redeemer + " tried to gift " + player.getName() + " a cat with a prohibited character in its name.");
                        return false;
                    }
                    Location location = player.getLocation();
                    Cat cat = (Cat) player.getWorld().spawnEntity(location, EntityType.CAT);
                    cat.setTamed(true);
                    cat.setOwner(player);
                    cat.setCustomName(text);
                    announce(redeemer + " gifted " + player.getName() + " a cat!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_wolf",
                "Spawn Wolf",
                "Enter a name for the wolf:",
                true,
                3500,
                (player, redeemer, text) -> {
                    if (text.contains("$")) {
                        announce(redeemer + " tried to gift " + player.getName() + " a wolf with a prohibited character in its name.");
                        return false;
                    }
                    Location location = player.getLocation();
                    Wolf wolf = (Wolf) player.getWorld().spawnEntity(location, EntityType.WOLF);
                    wolf.setTamed(true);
                    wolf.setOwner(player);
                    wolf.setCustomName(text);
                    announce(redeemer + " gifted " + player.getName() + " a wolf!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_rainbow_sheep",
                "Spawn Rainbow Sheep",
                "Spawn a Rainbow Sheep! :D",
                false,
                2500,
                (player, redeemer, text) -> {
                    Location location = player.getLocation();
                    Sheep sheep = (Sheep) player.getWorld().spawnEntity(location, EntityType.SHEEP);
                    sheep.setCustomName("jeb_");
                    sheep.setLeashHolder(player);
                    announce(redeemer + " gifted " + player.getName() + " a rainbow sheep!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "jump_boost_3_for_2min",
                "Jump Boost III for 2 minutes",
                "Make me jump extra high!",
                false,
                250,
                (player, redeemer, text) -> {
                    player.addPotionEffects(List.of(new PotionEffect(PotionEffectType.JUMP, 20 * 120, 2)));
                    announce(redeemer + " gave " + player.getName() + " a jump boost!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "nausea_for_2min",
                "Nausea for 2 minutes",
                "I can't see clearly!",
                false,
                250,
                (player, redeemer, text) -> {
                    player.addPotionEffects(List.of(new PotionEffect(PotionEffectType.CONFUSION, 20 * 120, 0)));
                    announce(redeemer + " gave " + player.getName() + " nausea!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "haste_5_for_2min",
                "Haste V for 2 minutes",
                "Mine super fast!",
                false,
                250,
                (player, redeemer, text) -> {
                    player.addPotionEffects(List.of(new PotionEffect(PotionEffectType.FAST_DIGGING, 20 * 120, 4)));
                    announce(redeemer + " gave " + player.getName() + " Haste V!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "mining_fatigue_2_for_1min",
                "Mining Fatigue II for 1 minute",
                "Mine super slow!",
                false,
                250,
                (player, redeemer, text) -> {
                    player.addPotionEffects(List.of(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20 * 60, 1)));
                    announce(redeemer + " gave " + player.getName() + " Haste V!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_64_torches",
                "Spawn 64 torches",
                "This will help if I forgot my torches!",
                false,
                350,
                (player, redeemer, text) -> {
                    give(player, new ItemStack(Material.TORCH, 64));
                    announce(redeemer + " gave " + player.getName() + " 64 torches!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_16_baked_potato",
                "Spawn 16 baked potatoes",
                "This will help if I'm low on food!",
                false,
                500,
                (player, redeemer, text) -> {
                    give(player, new ItemStack(Material.BAKED_POTATO, 16));
                    announce(redeemer + " gave " + player.getName() + " 16 baked potatoes!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_diamond_block",
                "Spawn a diamond block",
                "Give me a diamond block!",
                false,
                2500,
                (player, redeemer, text) -> {
                    give(player, new ItemStack(Material.DIAMOND_BLOCK, 1));
                    announce(redeemer + " gave " + player.getName() + " a diamond block!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "spawn_netherite_ingot",
                "Spawn a netherite ingot",
                "Give me a netherite ingot!",
                false,
                4500,
                (player, redeemer, text) -> {
                    give(player, new ItemStack(Material.NETHERITE_INGOT, 1));
                    announce(redeemer + " gave " + player.getName() + " a netherite ingot!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "rain_thunder",
                "Start raining and thundering",
                "Make it rain!",
                false,
                500,
                (player, redeemer, text) -> {
                    for (World w : Bukkit.getWorlds()) {
                        if (w.getEnvironment() == World.Environment.NORMAL) {
                            w.setThunderDuration(20 * 600);
                        }
                    }
                    announce(redeemer + " (a viewer on " + player.getName() + "'s channel) made it start raining and thundering!");
                    return true;
                }
        ));
        addReward(new ChannelPointReward(
                "fast_growing_100x",
                "100x Growing Speed",
                "Make everything grow 100x the normal speed for 5 minutes!",
                false,
                500,
                (player, redeemer, text) -> {
                    MineChannelPoints.getInstance().addFastGrowthTime(300);
                    announce(redeemer + " (a viewer on " + player.getName()  + "'s channel) made everything grow fast for 5 minutes!");
                    return true;
                }
        ));
    }

    public static Map<String, ChannelPointReward> get() {
        return immutableRewards;
    }

    public static ChannelPointReward get(String id) {
        return rewards.get(id);
    }
}

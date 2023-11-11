package io.siggi.minechannelpoints;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ChannelPointAction {
    boolean run(Player player, String redeemer, String text);
}

package io.siggi.minechannelpoints;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ChannelPointsCommand implements CommandExecutor, TabCompleter {

    private final MineChannelPoints plugin;
    private final String loginEndpoint;

    public ChannelPointsCommand(MineChannelPoints plugin, String loginEndpoint) {
        this.plugin = plugin;
        this.loginEndpoint = loginEndpoint;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] split) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        LoginCode loginCode = plugin.generateLoginCode(player.getUniqueId());
        TextComponent message = new TextComponent("Click here to add Channel Point Rewards (link expires in 30 seconds)");
        message.setColor(ChatColor.AQUA);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, loginEndpoint + "?code=" + loginCode.code()));
        player.spigot().sendMessage(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] split) {
        return List.of();
    }
}

package net.lnfinity.HeroBattle.Listeners;

import net.lnfinity.HeroBattle.HeroBattle;
import net.lnfinity.HeroBattle.Class.MaiteClass;
import net.lnfinity.HeroBattle.Class.PlayerClass;
import net.lnfinity.HeroBattle.Game.GamePlayer;
import net.md_5.bungee.api.ChatColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClassSelectionCommand implements CommandExecutor {

	private HeroBattle p;
	
	public ClassSelectionCommand(HeroBattle plugin) {
		p = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(args == null || args.length == 0) {
			sender.sendMessage(ChatColor.RED + "/classe <classe>");
		} else {
			Player player = p.getServer().getPlayer(sender.getName());
			if(player != null) {
				GamePlayer gamePlayer = p.getGamePlayer(player);
				if(args[0].equalsIgnoreCase("maite") || args[0].equalsIgnoreCase("maité") || args[0].equalsIgnoreCase("maïté")) {
					PlayerClass theClass = new MaiteClass(p);
					gamePlayer.setPlayerClass(theClass);
					player.sendMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + "Vous avez choisi la classe "
							+ ChatColor.DARK_GREEN + theClass.getName() + ChatColor.GREEN + " !");
					return true;
				}
				for(PlayerClass theClass : gamePlayer.getAvaibleClasses()) {
					if(args[0].equalsIgnoreCase(theClass.getType().getId())) {
						gamePlayer.setPlayerClass(theClass);
						player.sendMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + "Vous avez choisi la classe "
								+ ChatColor.DARK_GREEN + theClass.getName() + ChatColor.GREEN + " !");
						return true;
					}
				}
				player.sendMessage(ChatColor.RED + "Vous ne possédez pas cette classe !");
			}
		}
		return true;
	}

}
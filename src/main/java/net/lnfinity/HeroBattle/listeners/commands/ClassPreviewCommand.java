package net.lnfinity.HeroBattle.listeners.commands;

import net.lnfinity.HeroBattle.HeroBattle;
import net.lnfinity.HeroBattle.classes.PlayerClass;
import net.lnfinity.HeroBattle.game.HeroBattlePlayer;
import net.lnfinity.HeroBattle.gui.ClassDetailsGui;
import net.lnfinity.HeroBattle.gui.core.Gui;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/*
 * This file is part of HeroBattle.
 *
 * HeroBattle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HeroBattle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HeroBattle.  If not, see <http://www.gnu.org/licenses/>.
 */
public class ClassPreviewCommand implements CommandExecutor
{

	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args)
	{
		if (!(sender instanceof Player))
		{
			sender.sendMessage("Only as a player.");
			return true;
		}

		if (args == null || args.length == 0)
		{
			sender.sendMessage(ChatColor.RED + "/" + command.getName() + " <classe>");
			return true;
		}

		HeroBattlePlayer target = HeroBattle.get().getGamePlayer(((Player) sender).getUniqueId());
		if (target == null)
		{
			sender.sendMessage(ChatColor.RED + "Vous n'êtes pas un joueur !");
			return true;
		}

		PlayerClass playerClass = HeroBattle.get().getClassManager().getAnyClassByFriendlyName(args[0], target);
		if (playerClass != null)
		{
			Gui.open(((Player) sender), new ClassDetailsGui(playerClass));
		}
		else
		{
			sender.sendMessage(ChatColor.RED + "Classe inconnue. " + ChatColor.GRAY + "Vérifiez l'orthographe ?");
		}

		return true;
	}
}

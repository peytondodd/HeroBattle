package net.lnfinity.HeroBattle.classes.displayers.free;

import net.lnfinity.HeroBattle.HeroBattle;
import net.lnfinity.HeroBattle.classes.PlayerClass;
import net.lnfinity.HeroBattle.classes.PlayerClassType;
import net.lnfinity.HeroBattle.tools.displayers.ArrowsTool;
import net.lnfinity.HeroBattle.tools.displayers.NotYetAvaibleTool;
import net.lnfinity.HeroBattle.tools.displayers.SmokeTool;
import net.lnfinity.HeroBattle.tools.displayers.SpeedTool;
import net.lnfinity.HeroBattle.tools.displayers.weapons.SaberSwordTool;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

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
public class ArcherClass extends PlayerClass
{

	public ArcherClass(HeroBattle plugin)
	{
		this(plugin, 0, 0, 0);
	}

	public ArcherClass(HeroBattle plugin, int arg1, int arg2, int arg3)
	{
		super(plugin, arg1, arg2, arg3);

		addTool(new SaberSwordTool(p));
		addTool(new ArrowsTool(p, 15 - arg1, 3 + arg2, 8 + arg2 * 2, 20 + arg2 * 2, 20 + arg2 * 2, (int) (40 + 2.5 * arg2)));
		addTool(new SpeedTool(p, 20 - arg1 * 2, (int) Math.floor(1 + arg2 * 0.5), 5 + arg2));
		if (arg3 >= 1) addTool(new SmokeTool(p, 45 - arg1, 4 + arg2));
		if (arg3 >= 2) addTool(new NotYetAvaibleTool(p));
	}

	@Override
	public String getName()
	{
		return "Archer";
	}

	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Material.BOW);
	}

	@Override
	public ItemStack getHat()
	{
		ItemStack item = new ItemStack(Material.STAINED_GLASS);
		item.setDurability((short) 12);
		return item;
	}

	@Override
	public List<String> getDescription()
	{
		return Arrays.asList("La maitrise d'un arc et de ses flèches", "n'est pas donnée à tout le monde.", "", ChatColor.GRAY + "Classe de type " + ChatColor.GOLD + "Distant", ChatColor.GREEN + "+ " + ChatColor.GRAY + "Dégâts à distance, agilité, précision", ChatColor.RED + "- " + ChatColor.GRAY + "Peu résistant, corps à corps");
	}

	@Override
	public int getMinDamages()
	{
		return 2;
	}

	@Override
	public int getMaxDamages()
	{
		return 4;
	}

	@Override
	public int getMaxResistance()
	{
		return 145;
	}

	@Override
	public int getLives()
	{
		return 4;
	}

	@Override
	public PlayerClassType getType()
	{
		return PlayerClassType.ARCHER;
	}

}

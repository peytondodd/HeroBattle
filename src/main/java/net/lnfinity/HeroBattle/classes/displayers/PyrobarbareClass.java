package net.lnfinity.HeroBattle.classes.displayers;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.lnfinity.HeroBattle.HeroBattle;
import net.lnfinity.HeroBattle.classes.PlayerClass;
import net.lnfinity.HeroBattle.classes.PlayerClassType;
import net.lnfinity.HeroBattle.tools.displayers.FireTool;
import net.lnfinity.HeroBattle.tools.displayers.FireballTool;
import net.lnfinity.HeroBattle.tools.displayers.SwordVariant6Tool;
import net.md_5.bungee.api.ChatColor;

public class PyrobarbareClass extends PlayerClass {
	
	public PyrobarbareClass(HeroBattle plugin) {
		this(plugin, 0, 0, 0);
	}

	public PyrobarbareClass(HeroBattle plugin, int arg1, int arg2, int arg3) {
		super(plugin);
		
		addTool(new SwordVariant6Tool(p));
		addTool(new FireballTool(p, 30 - arg1 * 2));
		addTool(new FireTool(p, 60 - arg1 * 2, 10 + arg2));
	}

	@Override
	public String getName() {
		return "Pyrobarbare";
	}

	@Override
	public ItemStack getIcon() {
		return new ItemStack(Material.FLINT_AND_STEEL);
	}

	@Override
	public ItemStack getHat() {
		ItemStack item = new ItemStack(Material.STAINED_GLASS);
		item.setDurability((short) 1);
		return item;
	}

	@Override
	public List<String> getDescription() {
		return Arrays.asList("Ça va chauffer !", "", ChatColor.GRAY + "Classe de type " + ChatColor.GOLD + "Distant", ChatColor.GREEN + "+ " + ChatColor.GRAY + "Très puissant, plusieurs types de combat", ChatColor.RED + "- " + ChatColor.GRAY + "Peu agile, faible résistance");
	}

	@Override
	public int getMinDamages() {
		return 5;
	}

	@Override
	public int getMaxDamages() {
		return 8;
	}

	@Override
	public int getMaxResistance() {
		return 175;
	}

	@Override
	public int getLives() {
		return 3;
	}

	@Override
	public PlayerClassType getType() {
		return PlayerClassType.PYROBARBARE;
	}

}
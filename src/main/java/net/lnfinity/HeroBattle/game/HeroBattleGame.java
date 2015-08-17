package net.lnfinity.HeroBattle.game;

import net.lnfinity.HeroBattle.*;
import net.lnfinity.HeroBattle.classes.*;
import net.lnfinity.HeroBattle.classes.displayers.free.*;
import net.lnfinity.HeroBattle.tools.*;
import net.lnfinity.HeroBattle.utils.*;
import net.lnfinity.HeroBattle.utils.Utils;
import net.minecraft.server.v1_8_R3.*;
import net.samagames.api.*;
import net.samagames.api.games.*;
import net.samagames.tools.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.entity.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;


public class HeroBattleGame extends Game<HeroBattlePlayer>
{
	private final Integer COINS_PER_KILL = 5;
	private final Integer COINS_PER_ASSIST = 3;
	private final Integer COINS_PER_VICTORY = 16;
	private final Integer STARS_PER_VICTORY = 1;
	private final Integer COINS_IF_FIRST_RANKED = 10;
	private final Integer COINS_IF_SECOND_RANKED = 6;
	private final Integer COINS_IF_THIRD_RANKED = 4;

	private HeroBattle p;

	private List<Location> spawnPoints = new LinkedList<>();
	private Location hub;
	private Double bottomHeight = 0.0;
	private ArrayList<Location> tutorialLocations = new ArrayList<Location>();
	private ArrayList<Location> teleportationPortalsDestinations = new ArrayList<Location>();

	/**
	 * We store here the last players who launched a lightning bolt and where, to associate the
	 * damages to the good damager.
	 */
	private Map<UUID, Location> lastLightningBolts = new ConcurrentHashMap<>();

	/**
	 * Map player -> who poisonned him
	 */
	private Map<UUID, UUID> poisonsInProgress = new ConcurrentHashMap<>();

	/**
	 * Map player -> who inflammed him
	 */
	private Map<UUID, UUID> firesInProgress = new ConcurrentHashMap<>();

	/**
	 * Map fireballs UUID -> who launched them
	 */
	private Map<UUID, UUID> fireballsLaunched = new ConcurrentHashMap<>();

	/**
	 * Attaches stats to an entity (such as power, range...)
	 */
	private Map<UUID, TripleParameters> entitiesData = new ConcurrentHashMap<>();

	private Random random = new Random();

	private int damagesMultiplicator = 1;


	public HeroBattleGame()
	{
		super("herobattle", "HeroBattle", HeroBattlePlayer.class);

		p = HeroBattle.get();

		// Loads the spawn points and the hub from the world config.

		try
		{
			hub = Utils.stringToLocation(p, p.getArenaConfig().getString("map.hub"));
		}
		catch (IllegalArgumentException e)
		{
			p.getLogger().log(Level.SEVERE, "Invalid hub in arena.yml! " + e.getMessage());
		}

		p.getArenaConfig().getList("map.spawns").stream()
				.filter(spawn -> spawn instanceof String)
				.forEach(spawn -> {
					try
					{
						spawnPoints.add(Utils.stringToLocation(p, (String) spawn));
					}
					catch (IllegalArgumentException e)
					{
						p.getLogger().log(Level.SEVERE, "Invalid spawn in arena.yml! " + e.getMessage());
					}
				});

		if (spawnPoints.size() < getTotalMaxPlayers())
		{
			p.getLogger().severe("#==================[Fatal exception report]==================#");
			p.getLogger().severe("# Not enough spawn points set in the configuration.          #");
			p.getLogger().severe("# The plugin cannot load, please fix that.                   #");
			p.getLogger().severe("#============================================================#");

			p.getServer().getPluginManager().disablePlugin(p);
		}

		bottomHeight = p.getArenaConfig().getDouble("map.bottom", 0d);

		try
		{
			p.getArenaConfig().getList("map.tutorial").stream()
					.filter(location -> location instanceof String)
					.forEach(location -> {
						try
						{
							tutorialLocations.add(Utils.stringToLocation(p, (String) location));
						}
						catch (IllegalArgumentException e)
						{
							p.getLogger().log(Level.SEVERE, "Invalid tutorial locations in arena.yml! " + e.getMessage());
						}
					});

			if (tutorialLocations != null)
			{
				if (tutorialLocations.size() != 4)
				{
					p.getLogger().warning("Not enough / too many tutorial locations in arena.yml, disabling tutorial.");
					tutorialLocations = null;
				}
			}
		}
		catch (Exception ex)
		{
			p.getLogger().warning("No tutorial locations set in arena.yml");
		}


		try
		{
			p.getArenaConfig().getList("map.teleportationPortalsDestinations").stream()
					.filter(location -> location instanceof String)
					.forEach(location -> {
						try
						{
							teleportationPortalsDestinations.add(Utils.stringToLocation(p, (String) location));
						}
						catch (IllegalArgumentException e)
						{
							p.getLogger().log(Level.SEVERE, "Invalid teleportation portal location in arena.yml! " + e.getMessage());
						}
					});
		}
		catch (Exception ex)
		{
			p.getLogger().info("No teleportation portal locations set in arena.yml. Feature disabled.");
		}
	}

	/**
	 * Starts the game, handles everything including teleportations, messages, task timers... Should
	 * be called once.
	 */
	@Override
	public void startGame()
	{
		if (getStatus() == Status.IN_GAME) return;

		super.startGame();

		p.getTutorialDisplayer().stopForAll("Le jeu démarre...");

		Integer partyELO = getTotalElo() / getConnectedPlayers();

		p.getServer().broadcastMessage(HeroBattle.GAME_TAG + ChatColor.DARK_GREEN + "ELO" + ChatColor.GREEN + " de la partie " + ChatColor.DARK_GREEN + partyELO);
		p.getServer().broadcastMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + "Que le meilleur gagne !");

		p.getServer().getWorlds().get(0).setFullTime(p.getArenaConfig().getLong("map.dayTime"));

		teleportPlayers();

		p.getGameTimer().startTimer();
		p.getScoreboardManager().init();
		p.getPowerupManager().getSpawner().startTimer();

		for (Player player : p.getServer().getOnlinePlayers())
		{
			Titles.sendTitle(player, 2, 38, 6, ChatColor.AQUA + "C'est parti !", ChatColor.GREEN + "ELO de la partie : " + ChatColor.DARK_GREEN + partyELO);

			increaseStat(player.getUniqueId(), "played", 1);

			ActionBar.removeMessage(player);
		}

		p.getServer().getScheduler().runTaskLater(p, () -> {
			// Toxic water
			if (p.getArenaConfig().getBoolean("map.toxicWater", false))
			{

				// Chat
				p.getServer().broadcastMessage("");
				p.getServer().broadcastMessage(ChatColor.DARK_AQUA + "-----------------------------------------------------");
				p.getServer().broadcastMessage(ChatColor.DARK_AQUA + "[×] " + ChatColor.AQUA + "Attention, l'eau est " + ChatColor.DARK_AQUA + "toxique" + ChatColor.AQUA + " sur cette carte !");
				p.getServer().broadcastMessage(ChatColor.DARK_AQUA + "-----------------------------------------------------");
				p.getServer().broadcastMessage("");

				// Title
				for (Player player : p.getServer().getOnlinePlayers())
				{
					Titles.sendTitle(player, 15, 40, 15, ChatColor.DARK_AQUA + "\u26A0", ChatColor.AQUA + "L'eau est " + ChatColor.DARK_AQUA + "toxique" + ChatColor.AQUA + " ici !");
				}
			}

			// Toxic lava
			if (p.getArenaConfig().getBoolean("map.toxicLava", false))
			{
				p.getServer().broadcastMessage("");
				p.getServer().broadcastMessage(ChatColor.DARK_RED + "-----------------------------------------------------");
				p.getServer().broadcastMessage(ChatColor.DARK_RED + "[×] " + ChatColor.GOLD + "Attention, la lave est " + ChatColor.RED + "instantanément mortelle" + ChatColor.GOLD + " ici !");
				p.getServer().broadcastMessage(ChatColor.DARK_RED + "-----------------------------------------------------");
				p.getServer().broadcastMessage("");

				// Title
				for (Player player : p.getServer().getOnlinePlayers())
				{
					Titles.sendTitle(player, 15, 40, 15, ChatColor.DARK_RED + "\u26A0", ChatColor.GOLD + "La lave est " + ChatColor.RED + "instantanément mortelle" + ChatColor.GOLD + " !");
				}
			}

		}, 70l);
	}

	/**
	 * Teleports every playing players to a random spot and fills inventories. Should be called
	 * once.
	 */
	public void teleportPlayers()
	{
		List<Location> tempLocs = new LinkedList<>(spawnPoints);
		Random rand = new Random();

		for (HeroBattlePlayer hbPlayer : gamePlayers.values())
		{
			Player player = hbPlayer.getPlayerIfOnline();

			try
			{
				if (player == null) continue;

				if (hbPlayer.getPlayerClass() == null)
				{
					chooseRandomClass(player);
				}

				CraftPlayer cp = (CraftPlayer) player;
				EntityPlayer ep = cp.getHandle();
				int ping = ep.ping;
				if (ping > 500)
				{
					player.sendMessage("");
					player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Attention" + ChatColor.RED + ", nous venons de détecter que votre connexion est instable. Des effets secondaires peuvent se faire ressentir en jeu.");
					player.sendMessage("");
				}

				int index = rand.nextInt(tempLocs.size());
				player.teleport(tempLocs.get(index));
				tempLocs.remove(index);

				player.getInventory().clear();
				player.setLevel(0);

				player.setAllowFlight(true);
				player.setFlySpeed(0.00F);

				p.getGame().updatePlayerArmor(player);

				int i = 0;
				for (PlayerTool tool : hbPlayer.getPlayerClass().getTools())
				{
					player.getInventory().setItem(i, tool.generateCompleteItem());
					i++;
				}

				player.getInventory().setHeldItemSlot(0);
				player.updateInventory();

				player.setGameMode(GameMode.ADVENTURE);
				player.setMaxHealth(hbPlayer.getPlayerClass().getLives() * 2);
				player.setHealth(hbPlayer.getPlayerClass().getLives() * 2d);
			}
			catch (Exception e)
			{
				// Temp. workaround against game crash at start
				p.getLogger().severe("Exception occurred while teleporting a player (" + player + ")!");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Teleport the player to the map's hub. (Teleportation only, nothing else is edited)
	 *
	 * @param id
	 */
	public void teleportHub(UUID id)
	{
		p.getServer().getPlayer(id).teleport(hub);
	}

	/**
	 * Teleport the player to a registered random spot on the map. (Teleportation only, nothing else
	 * is edited)
	 *
	 * @param id
	 */
	public void teleportRandomSpot(UUID id)
	{
		teleportRandomSpot(p.getServer().getPlayer(id));
	}

	/**
	 * Teleport the player to a registered random spot on the map. (Teleportation only, nothing else
	 * is edited)
	 *
	 * @param player
	 */
	public void teleportRandomSpot(Player player)
	{
		p.getGame().updatePlayerArmor(player);
		player.teleport(spawnPoints.get(random.nextInt(spawnPoints.size())));
	}

	/**
	 * Makes the player respawns. Visual effects are processed.
	 *
	 * @param player
	 */
	public void respawnPlayer(Player player)
	{
		HeroBattlePlayer hbPlayer = getPlayer(player.getUniqueId());

		hbPlayer.resetPercentage();
		player.setExp(0);
		player.setLevel(0);
		player.setTotalExperience(0);

		player.setHealth(hbPlayer.getLives() * 2);

		teleportRandomSpot(player);
	}

	public void enableSpectatorMode(final Player player)
	{
		HeroBattlePlayer hbPlayer = getPlayer(player.getUniqueId());

		hbPlayer.setSpectator();

		player.setGameMode(GameMode.SPECTATOR);
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
		player.setFlySpeed(0.1F);

		teleportRandomSpot(player);
	}

	/**
	 * Chooses a random class according to the classes owned. Should be called just before the
	 * begining of the game, if the player hasn't choosen one yet.
	 *
	 * @param player
	 */
	public void chooseRandomClass(Player player)
	{
		HeroBattlePlayer heroBattlePlayer = getPlayer(player.getUniqueId());

		if (heroBattlePlayer == null || heroBattlePlayer.getAvaibleClasses().size() == 0) return;

		Random rnd = new Random();

		int r = rnd.nextInt(heroBattlePlayer.getAvaibleClasses().size());
		int i = 0;

		// Avoid game crashing
		if (heroBattlePlayer.getAvaibleClasses() == null || heroBattlePlayer.getAvaibleClasses().isEmpty())
		{
			heroBattlePlayer.setPlayerClass(new BruteClass(p, 0, 0, 0));
			return;
		}

		for (PlayerClass classe : heroBattlePlayer.getAvaibleClasses())
		{
			if (i == r)
			{
				heroBattlePlayer.setPlayerClass(classe);
				player.sendMessage(ChatColor.GREEN + "La classe " + ChatColor.DARK_GREEN + classe.getName()
						+ ChatColor.GREEN + " vous a été attribuée suite à un complexe jeu de dés !");
				return;
			}
			i++;
		}
	}

	/**
	 * Handles the player death. Also handles elimination.
	 *
	 * @param id    the player
	 * @param death the way he died
	 */
	public void onPlayerDeath(UUID id, DeathType death)
	{
		if (getStatus() != Status.IN_GAME)
		{
			teleportHub(id);
			return;
		}

		final Player player = p.getServer().getPlayer(id);
		final HeroBattlePlayer hbPlayer = getPlayer(player.getUniqueId());

		// Avoid the death to be handled if the player is already completely dead (no life left)
		if (hbPlayer.getTotalLives() <= 0) return;


		// Avoids this to be called a lot of times
		if (hbPlayer.isDeathHandled()) return;

		hbPlayer.setDeathHandled(true);
		p.getServer().getScheduler().runTaskLater(p, () -> hbPlayer.setDeathHandled(false), 15l);


		// Technical stuff
		hbPlayer.looseLife();
		player.setVelocity(new Vector());

		final Location deathLocation = player.getLocation();

		// Broadcasts
		String s = hbPlayer.getTotalLives() <= 1 ? "" : "s";
		String lives = ChatColor.DARK_GRAY + " (" + ChatColor.RED + hbPlayer.getTotalLives() + ChatColor.DARK_GRAY
				+ " vie" + s + ")";

		Player lastDamagerPlayer = hbPlayer.getLastDamager() != null ? p.getServer().getPlayer(hbPlayer.getLastDamager()) : null;
		HeroBattlePlayer lastDamagerGPlayer = lastDamagerPlayer != null ? getPlayer(lastDamagerPlayer.getUniqueId()) : null;

		String killedByMessage = hbPlayer.getTotalLives() >= 1 ? ChatColor.RED + "Vous perdez une vie !" : ChatColor.RED + "C'est fini pour vous !";

		if (lastDamagerPlayer == null)
		{
			p.getServer().broadcastMessage(HeroBattle.GAME_TAG + death.getMessageSoloDeath(player.getName()) + lives);
		}
		else
		{
			String groupColor = Utils.getPlayerColor(lastDamagerPlayer);

			if (player.getUniqueId().equals(lastDamagerPlayer.getUniqueId()))
			{
				p.getServer().broadcastMessage(HeroBattle.GAME_TAG + death.getMessageSuicide(player.getName()) + lives);
			}
			else
			{
				p.getServer().broadcastMessage(
						HeroBattle.GAME_TAG
								+ death.getMessageCausedDeath(ChatColor.DARK_RED + player.getName(), ChatColor.DARK_GREEN + lastDamagerPlayer.getName())
								+ lives
				);

				killedByMessage = groupColor + lastDamagerPlayer.getName() + ChatColor.RED + " vous a éjecté !";

				increaseStat(lastDamagerGPlayer.getUUID(), "kills", 1);
				lastDamagerGPlayer.addCoins(COINS_PER_KILL, death.getCoinsMessage());
			}

			lastDamagerGPlayer.addPlayersKilled();
		}


		// Assist (only for real deaths)
		if (lastDamagerGPlayer != null && !lastDamagerGPlayer.isSpectator())
		{
			if (death != DeathType.QUIT)
			{
				// An assist is agreed only if:
				//  - the player made more than 38% of the whole damages made to the player, or
				//  - the player made at least 20 damages in the last ten seconds.
				final Double ASSIST_WHOLE_DAMAGES_PERCENTAGE_MIN = 0.38;
				final Integer ASSIST_RECENT_DAMAGES_MIN = 20;
				final Long ASSIST_RECENT_DAMAGES_TIME = 10000l; // 10 seconds

				Integer minimalDamages = ((int) (hbPlayer.getPercentage() * ASSIST_WHOLE_DAMAGES_PERCENTAGE_MIN));

				for (Map.Entry<UUID, Assist> assistEntry : hbPlayer.getAssists().entrySet())
				{
					Assist assist = assistEntry.getValue();
					UUID uuid = assistEntry.getKey();

					if (uuid.equals(hbPlayer.getLastDamager())) continue;

					if (assist.getTotalAssist() >= minimalDamages || assist.getRecentAssists(ASSIST_RECENT_DAMAGES_TIME) >= ASSIST_RECENT_DAMAGES_MIN)
					{
						increaseStat(uuid, "assists", 1);

						HeroBattlePlayer assistGPlayer = getPlayer(uuid);
						if (assistGPlayer != null)
						{
							assistGPlayer.addCoins(COINS_PER_ASSIST, "Assistance contre " + hbPlayer.getOfflinePlayer().getName() + " !");
						}
					}
				}
			}
		}

		hbPlayer.resetAssists();


		// Effects on the player
		for (PotionEffect effect : player.getActivePotionEffects())
		{
			// Clears current effects
			player.removePotionEffect(effect.getType());
		}

		player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
		p.getServer().getScheduler().runTaskLater(p, () -> player.playSound(player.getLocation(), Sound.IRONGOLEM_DEATH, 1, 1), 5l);

		for (long i = 1l; i <= 10l; i += 4l)
		{
			p.getServer().getScheduler().runTaskLater(p, () -> deathLocation.getWorld().strikeLightningEffect(deathLocation), i);
		}


		// Removes the fire
		p.getServer().getScheduler().runTaskLater(p, () -> player.setFireTicks(0), 5l);


		// Death message
		String titleMessage;
		if (lastDamagerPlayer == null)
		{
			titleMessage = death.getTitleSoloDeath();
		}
		else if (lastDamagerPlayer.getUniqueId().equals(player.getUniqueId()))
		{
			titleMessage = death.getTitleSuicide();
		}
		else
		{
			titleMessage = death.getTitleCausedDeath(lastDamagerPlayer.getDisplayName());
		}

		final String finalKilledByMessage = titleMessage.isEmpty() ? killedByMessage : titleMessage;
		Integer titleStayDuration = hbPlayer.getLives() >= 1 ? 50 : 100;
		Integer titleFadeOutDuration = hbPlayer.getLives() >= 1 ? 8 : 18;

		Titles.sendTitle(player, 3, 150, 0, Utils.heartsToString(hbPlayer, true, false), ChatColor.RED + killedByMessage);
		p.getServer().getScheduler().runTaskLater(p, () -> Titles.sendTitle(player, 0, titleStayDuration, titleFadeOutDuration, Utils.heartsToString(hbPlayer, false, true), ChatColor.RED + finalKilledByMessage), 10L);

		// Respawn
		if (hbPlayer.getLives() >= 1)
		{
			hbPlayer.setRespawning();
			respawnPlayer(player);

			// Très important ! Sinon le joueur conserve sa vélocité
			player.setVelocity(player.getVelocity().zero());

			// Effet de lenteur pour éviter les chutes non désirées
			player.setAllowFlight(true);
			player.setFlying(true);
			p.getServer().getScheduler().runTaskLater(p, () -> player.setFlying(false), 20L); // Annulation
		}

		// Spectator mode
		else
		{
			enableSpectatorMode(player);

			final int alivePlayersCount = getInGamePlayers().size();

			s = alivePlayersCount <= 1 ? "" : "s";

			p.getServer().broadcastMessage(
					HeroBattle.GAME_TAG + ChatColor.DARK_RED + player.getName() + ChatColor.YELLOW + " a perdu ! "
							+ ChatColor.DARK_GRAY + "[" + ChatColor.RED + alivePlayersCount
							+ ChatColor.DARK_GRAY + " joueur" + s + ChatColor.DARK_GRAY + "]");


			if (alivePlayersCount == 1)
			{
				for (HeroBattlePlayer pl : gamePlayers.values())
				{
					if (!pl.isSpectator())
					{
						onPlayerWin(pl.getUUID());
						return;
					}
				}
			}
		}


		// Scoreboard update
		p.getScoreboardManager().refresh();


		// Stats
		increaseStat(player.getUniqueId(), "deaths", 1);
	}


	/**
	 * Handles a player disconnection.
	 *
	 * @param player The player who just disconnected.
	 */
	@Override
	public void handleLogout(Player player)
	{
		UUID id = player.getUniqueId();

		HeroBattlePlayer gPlayer = getPlayer(id);
		if (gPlayer == null) return;


		final int alivePlayersCount = getInGamePlayers().size();

		if (!gPlayer.isSpectator())
		{
			gPlayer.setSpectator();
			gPlayer.setLives(0);

			String s = "s";
			if (alivePlayersCount == 1)
				s = "";

			p.getServer().broadcastMessage(
					HeroBattle.GAME_TAG + ChatColor.YELLOW + p.getServer().getPlayer(id).getDisplayName()
							+ ChatColor.YELLOW + " a perdu ! " + ChatColor.DARK_GRAY + "[" + ChatColor.RED
							+ (alivePlayersCount - 1) + ChatColor.DARK_GRAY + " joueur" + s +
							ChatColor.DARK_GRAY + "]");

			p.getScoreboardManager().refresh();
		}

		if (alivePlayersCount == 1)
		{
			for (HeroBattlePlayer pl : gamePlayers.values())
			{
				if (!pl.isSpectator())
				{
					onPlayerWin(pl.getUUID());
					return;
				}
			}

			onPlayerWin(null);
		}
	}

	/**
	 * Call this when the game is finished.
	 *
	 * @param id The UUID of the winner. If {@code null}, no winner for this game (counter timed
	 *           out, as example).
	 */
	public void onPlayerWin(UUID id)
	{
		handleGameEnd();

		p.getScoreboardManager().refresh();
		p.getScoreboardManager().refreshTab();

		if (id != null)
		{
			final Player winner = p.getServer().getPlayer(id);
			HeroBattlePlayer gWinner = getPlayer(id);

			winner.getInventory().clear();

			gWinner.setSpectator();


			Bukkit.getScheduler().runTaskLater(HeroBattle.get(), () -> {

				// TODO Coherence Machine
				Bukkit.broadcastMessage("");
				Bukkit.broadcastMessage(ChatColor.GOLD + "----------------------------------------------------");
				Bukkit.broadcastMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + winner.getDisplayName() + ChatColor.GREEN + ChatColor.BOLD + " remporte la partie !");
				Bukkit.broadcastMessage(ChatColor.GOLD + "----------------------------------------------------");
				Bukkit.broadcastMessage("");

				new WinnerFirework(p, 30, winner);


				String winnerDisplayName = Utils.getPlayerColor(winner);
				if (winner.getUniqueId().equals(UUID.fromString("0dd34bda-c13b-473b-a887-368027ca05ca"))) // Jenjeur
				{
					winnerDisplayName += "\u2708  " + winner.getName() + "  \u2708";
				}
				else if (winner.getUniqueId().equals(UUID.fromString("7caf2af6-b149-47eb-8b76-7f58c07d8f5a"))) // Vayan91
				{
					winnerDisplayName += ChatColor.GOLD + "\u272F  " + Utils.getPlayerColor(winner) + winner.getName() + ChatColor.GOLD + "  \u272F";
				}
				else if (winner.getUniqueId().equals(UUID.fromString("da04cd54-c6c7-4672-97c5-85663f5bccf6"))) // AmauryPi
				{
					winnerDisplayName += winner.getName() + ChatColor.GRAY + " (nofake)";
				}
				else if (winner.getUniqueId().equals(UUID.fromString("95dec9f8-ed6d-4aa1-b787-e776adabcec6"))) // Hi_im_Pichu
				{
					winnerDisplayName += ChatColor.GOLD + "\u26A1 " + ChatColor.YELLOW + winner.getName() + ChatColor.GOLD + " \u26A1";
				}
				else
				{
					winnerDisplayName += winner.getName();
				}

				for (Player player : Bukkit.getOnlinePlayers())
				{
					Titles.sendTitle(player, 10, 100, 30, winnerDisplayName, ChatColor.YELLOW + "remporte la partie !");
				}

			}, 30l);


			gWinner.addStars(STARS_PER_VICTORY, "Victoire !");
			gWinner.addCoins(COINS_PER_VICTORY, "Victoire !");
			increaseStat(id, "wins", 1);
		}

		p.getPowerupManager().getSpawner().stopTimer();
		p.getGameTimer().pauseTimer();


		final Map<UUID, Long> percentagesInflicted = new TreeMap<>((a, b) -> {
			try
			{
				Long prcA = getPlayer(a).getPercentageInflicted();
				Long prcB = getPlayer(b).getPercentageInflicted();

				Integer killsA = getPlayer(a).getPlayersKilled();
				Integer killsB = getPlayer(b).getPlayersKilled();

				if (prcA > prcB) return -1;
				else if (prcA < prcB) return 1;

				else
				{
					if (killsA >= killsB) return -1;
					else return 1;
				}

			}
			catch (NullPointerException e)
			{
				return 0;
			}
		});

		final Map<UUID, Integer> kills = new TreeMap<>((a, b) -> {
			try
			{
				Long prcA = getPlayer(a).getPercentageInflicted();
				Long prcB = getPlayer(b).getPercentageInflicted();

				Integer killsA = getPlayer(a).getPlayersKilled();
				Integer killsB = getPlayer(b).getPlayersKilled();

				if (killsA > killsB) return -1;
				else if (killsA < killsB) return 1;

				else
				{
					if (prcA >= prcB) return -1;
					else return 1;
				}

			}
			catch (NullPointerException e)
			{
				return 0;
			}
		});

		int i = 1;
		for (HeroBattlePlayer player : gamePlayers.values())
		{
			percentagesInflicted.put(player.getUUID(), player.getPercentageInflicted());
			kills.put(player.getUUID(), player.getPlayersKilled());

			player.setPercentageRank(i);
			player.setKillsRank(i);

			i++;
		}

		calculateElos(id);


		for (final HeroBattlePlayer hbPlayer : gamePlayers.values())
		{
			SamaGamesAPI.get().getStatsManager(getGameCodeName()).setValue(hbPlayer.getUUID(), "elo", hbPlayer.getElo());
		}

		p.getServer().getScheduler().runTaskLater(p, () -> {
			for (HeroBattlePlayer heroBattlePlayer : gamePlayers.values())
			{
				Player player = p.getServer().getPlayer(heroBattlePlayer.getUUID());
				if (player != null)
				{
					int change = heroBattlePlayer.getElo() - heroBattlePlayer.getOriginalElo();
					if (change >= 0)
					{
						player.sendMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + "Votre " + ChatColor.DARK_GREEN + "ELO" + ChatColor.GREEN + " augmente de " + ChatColor.DARK_GREEN + change + ChatColor.GREEN + " (" + ChatColor.DARK_GREEN + heroBattlePlayer.getElo() + ChatColor.GREEN + ")");
					}
					else if (change < 0)
					{
						player.sendMessage(HeroBattle.GAME_TAG + ChatColor.GREEN + "Votre " + ChatColor.DARK_GREEN + "ELO" + ChatColor.GREEN + " diminue de " + ChatColor.RED + Math.abs(change) + ChatColor.GREEN + " (" + ChatColor.DARK_GREEN + heroBattlePlayer.getElo() + ChatColor.GREEN + ")");
					}
				}
			}
		}, 3 * 20l);


		// TODO migrate to coherence machine
		p.getServer().getScheduler().runTaskLaterAsynchronously(p, () -> {
			String[] topsPercentages = new String[] {"", "", ""};
			String[] topsKills = new String[] {"", "", ""};


			// Kills
			int i1 = 0;
			Iterator<Map.Entry<UUID, Integer>> iterKills = kills.entrySet().iterator();
			while (i1 < 3 && iterKills.hasNext())
			{
				Map.Entry<UUID, Integer> entry = iterKills.next();
				topsKills[i1] = Bukkit.getOfflinePlayer(entry.getKey()).getName() + ChatColor.AQUA + " (" + entry.getValue() + ")";

				HeroBattlePlayer gPlayer = getPlayer(entry.getKey());
				if (gPlayer != null)
				{
					gPlayer.addCoins(i1 == 0 ? COINS_IF_FIRST_RANKED : i1 == 1 ? COINS_IF_SECOND_RANKED : COINS_IF_THIRD_RANKED, "Rang " + (i1 + 1) + " au classement des kills !");
				}

				i1++;
			}

			// Percentages
			i1 = 0;
			Iterator<Map.Entry<UUID, Long>> iterPercentages = percentagesInflicted.entrySet().iterator();
			while (i1 < 3 && iterPercentages.hasNext())
			{
				Map.Entry<UUID, Long> entry = iterPercentages.next();
				topsPercentages[i1] = Bukkit.getOfflinePlayer(entry.getKey()).getName() + ChatColor.AQUA + " (" + Utils.formatNumber(entry.getValue()) + "%)";

				HeroBattlePlayer gPlayer = getPlayer(entry.getKey());
				if (gPlayer != null)
				{
					gPlayer.addCoins(i1 == 0 ? COINS_IF_FIRST_RANKED : i1 == 1 ? COINS_IF_SECOND_RANKED : COINS_IF_THIRD_RANKED, "Rang " + (i1 + 1) + " au classement des dégâts infligés !");
				}

				i1++;
			}

			Bukkit.broadcastMessage(ChatColor.GOLD + "----------------------------------------------------");
			Bukkit.broadcastMessage(ChatColor.GOLD + "                        Classement des Kills        ");
			Bukkit.broadcastMessage(ChatColor.GOLD + "                                                    ");
			Bukkit.broadcastMessage(ChatColor.YELLOW + " " + topsKills[0] + ChatColor.DARK_GRAY + (!topsKills[1].isEmpty() ? " ⋅ " : "") + ChatColor.GRAY + topsKills[1] + ChatColor.DARK_GRAY + (!topsKills[2].isEmpty() ? " ⋅ " : "") + ChatColor.GOLD + topsKills[2]);
			Bukkit.broadcastMessage(ChatColor.GOLD + "                                                    ");
			Bukkit.broadcastMessage(ChatColor.GOLD + "                  Classement des dégâts infligés    ");
			Bukkit.broadcastMessage(ChatColor.GOLD + "                                                    ");
			Bukkit.broadcastMessage(ChatColor.YELLOW + " " + topsPercentages[0] + ChatColor.DARK_GRAY + (!topsPercentages[1].isEmpty() ? " ⋅ " : "") + ChatColor.GRAY + topsPercentages[1] + ChatColor.DARK_GRAY + (!topsPercentages[2].isEmpty() ? " ⋅ " : "") + ChatColor.GOLD + topsPercentages[2]);
			Bukkit.broadcastMessage(ChatColor.GOLD + "                                                    ");
			Bukkit.broadcastMessage(ChatColor.GOLD + "----------------------------------------------------");

		}, 20 * 5l);


		// Analytics to help us improve the game
		if (!p.getArenaConfig().getBoolean("block-analytics"))
		{
			final String serverName = SamaGamesAPI.get().getServerName();
			final String mapName = p.getGame().getMapName();
			final Integer playersCount = getSpectatorPlayers().size(); // All players are now spectating.
			final String duration = p.getGameTimer().getFormattedTime();

			final String winnerName;
			final Integer winnerELO;
			final String winnerClass;

			if (id != null)
			{
				Player player = p.getServer().getPlayer(id);
				HeroBattlePlayer gPlayer = getPlayer(id);

				winnerName = player.getName();
				winnerELO = gPlayer.getElo();
				winnerClass = gPlayer.getPlayerClass().getType().toString().toLowerCase();
			}
			else
			{
				winnerName = "(No winner)";
				winnerELO = 0;
				winnerClass = "";
			}

			p.getServer().getScheduler().runTaskAsynchronously(p, () -> {
				try
				{
					URL u = new URL("http://lnfinity.net/tasks/herobattle-stats.php?v=1&s=" + URLEncoder.encode(serverName, "UTF-8") + "&m=" + URLEncoder.encode(mapName, "UTF-8") + "&p=" + playersCount + "&d=" + URLEncoder.encode(duration, "UTF-8") + "&w=" + URLEncoder.encode(winnerName, "UTF-8") + "&we=" + winnerELO + "&wc=" + URLEncoder.encode(winnerClass, "UTF-8"));
					u.openStream();
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			});
		}
	}

	/**
	 * Calculates the ELOs of the players.
	 *
	 * TODO Rewrite the ELO algorithm.
	 *
	 * @param winner The winner. Can be {@code null}.
	 */
	public void calculateElos(UUID winner)
	{
		double total = getTotalElo();

		for (HeroBattlePlayer heroBattlePlayer : gamePlayers.values())
		{
			double esp = (heroBattlePlayer.getElo() / total); // Espérance de gain pour heroBattlePlayer
			double k = 16;

			double extraK = heroBattlePlayer.getKillsRank() == 0 ? 0 : 4 - heroBattlePlayer.getKillsRank();
			double extraP = heroBattlePlayer.getPercentageRank() == 0 ? 0 : 4 - heroBattlePlayer.getPercentageRank();
			double extra = extraK + extraP; // Extra par rapport au classement

			final int playersCount = getSpectatorPlayers().size();

			if (heroBattlePlayer.getUUID() == winner) // Le joueur a gagné
			{
				int elo = heroBattlePlayer.getElo();
				k = 20 * playersCount * ((total / playersCount) / elo);
				int elo1 = (int) (k * (1 - esp));
				double mult = 1 / Utils.logb((elo + 1000) / 1000, 2);

				heroBattlePlayer.setElo((int) (mult * elo1 + elo));

				if (heroBattlePlayer.getElo() > 10000)
				{
					heroBattlePlayer.setElo(10000);
				}

			}
			else // Le joueur n'a pas gagné
			{
				int elo = heroBattlePlayer.getElo();
				k = 40 * elo / (total / playersCount);
				int elo1 = (int) (k * -esp);
				double mult = Utils.logb((elo + 1000) / 1000, 2);

				heroBattlePlayer.setElo((int) (mult * elo1 + elo));

				if (heroBattlePlayer.getElo() < 1000)
				{
					heroBattlePlayer.setElo(1000);
				}
			}
		}
	}

	/**
	 * @return the sum of all the players elos
	 */
	public int getTotalElo()
	{
		int total = 0; // Total

		for (HeroBattlePlayer heroBattlePlayer : gamePlayers.values())
		{
			if (heroBattlePlayer.getElo() < 1000) // Pour les nouveaux joueurs
			{
				heroBattlePlayer.setElo(2000);
			}

			else if (heroBattlePlayer.getElo() > 10000) // Ne devrait jamais arriver
			{
				heroBattlePlayer.setElo(10000);
			}

			total += heroBattlePlayer.getElo();
		}

		return total;
	}

	/**
	 * TODO include this in the SGAPI.
	 *
	 * @return All registered game players.
	 */
	public Map<UUID, HeroBattlePlayer> getGamePlayers()
	{
		return new HashMap<>(gamePlayers);
	}


	public int getMaxPlayers()
	{
		return p.getArenaConfig().getInt("map.maxPlayers");
	}


	public int getTotalMaxPlayers()
	{
		return getMaxPlayers() + getVIPSlots();
	}


	public int getVIPSlots()
	{
		return p.getArenaConfig().getInt("map.maxVIP");
	}


	public String getMapName()
	{
		return p.getArenaConfig().getString("map.name");
	}

	public int getMinPlayers()
	{
		return p.getArenaConfig().getInt("map.minPlayers");
	}

	public int getCountdownTime()
	{
		return p.getArenaConfig().getInt("map.waiting", 120);
	}

	/**
	 * Below this height, the players are dead.
	 */
	public Double getBottomHeight()
	{
		return bottomHeight;
	}


	public Map<UUID, Location> getLastLightningBolts()
	{
		return lastLightningBolts;
	}

	public Map<UUID, UUID> getPoisonsInProgress()
	{
		return poisonsInProgress;
	}

	public Map<UUID, UUID> getFiresInProgress()
	{
		return firesInProgress;
	}

	public Map<UUID, UUID> getFireballsLaunched()
	{
		return fireballsLaunched;
	}


	public ArrayList<Location> getTutorialLocations()
	{
		return tutorialLocations;
	}

	public ArrayList<Location> getTeleportationPortalsDestinations()
	{
		return teleportationPortalsDestinations;
	}


	/**
	 * Gets the first block targeted by the play.
	 *
	 * @param player
	 * @param maxRange
	 *
	 * @return
	 */
	public Block getTargetBlock(Player player, int maxRange)
	{
		Block block;
		Location loc = player.getEyeLocation().clone();
		Vector progress = loc.getDirection().normalize().clone().multiply(0.70);
		maxRange = (100 * maxRange / 70);
		int loop = 0;
		while (loop < maxRange)
		{
			loop++;
			loc.add(progress);
			block = loc.getBlock();
			if (!block.getType().equals(Material.AIR))
			{
				return loc.getBlock();
			}
		}
		return null;
	}

	/**
	 * Updates the color of the armor according to his percentage.
	 *
	 * @param player
	 */
	public void updatePlayerArmor(Player player)
	{

		HeroBattlePlayer heroBattlePlayer = getPlayer(player.getUniqueId());

		if (heroBattlePlayer == null || heroBattlePlayer.isSpectator()) return;


		if (heroBattlePlayer.getRemainingInvisibility() != 0)
		{
			player.getInventory().setArmorContents(null);
		}

		else
		{

			if (heroBattlePlayer.getPlayerClass() == null)
				heroBattlePlayer.setPlayerClass(new BruteClass(p));
			ItemStack hat = heroBattlePlayer.getPlayerClass().getHat();
			ItemMeta hatMeta = hat.getItemMeta();
			hatMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + heroBattlePlayer.getPlayerClass().getName());
			hatMeta.setLore(heroBattlePlayer.getPlayerClass().getClassDetailsLore());
			hat.setItemMeta(hatMeta);
			player.getInventory().setHelmet(hat);


			// Armor colors
			int R = 470 - heroBattlePlayer.getPercentage();
			int G = 255 - heroBattlePlayer.getPercentage();
			int B = 255 - heroBattlePlayer.getPercentage() * 2;

			if (R > 255) R = 255;
			else if (R < 0) R = 0;

			if (G > 255) G = 255;
			else if (G < 0) G = 0;

			if (B > 255) B = 255;
			else if (B < 0) B = 0;


			ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);

			LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
			meta.setColor(Color.fromRGB(R, G, B));
			meta.spigot().setUnbreakable(true);

			chest.setItemMeta(meta);

			ItemStack leg = new ItemStack(Material.LEATHER_LEGGINGS, 1);
			leg.setItemMeta(meta);

			ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
			boots.setItemMeta(meta);

			player.getInventory().setChestplate(chest);
			player.getInventory().setLeggings(leg);
			player.getInventory().setBoots(boots);
		}
	}

	/**
	 * Fills the player's inventory with lobby items such as the class selector, tutorial and exit
	 * item.
	 *
	 * @param player
	 */
	public void equipPlayer(Player player)
	{

		player.getInventory().clear();

		// Class selector
		ItemStack classSelectorItem = new ItemStack(Material.NETHER_STAR);
		ItemMeta classSelectorItemMeta = classSelectorItem.getItemMeta();
		classSelectorItemMeta.setDisplayName(
				ChatColor.LIGHT_PURPLE + "Choisissez une " + ChatColor.DARK_PURPLE + "classe"
		);
		classSelectorItemMeta.setLore(Arrays.asList(
				ChatColor.GRAY + "Cliquez-droit pour choisir la classe",
				ChatColor.GRAY + "avec laquelle vous allez jouer."
		));
		classSelectorItem.setItemMeta(classSelectorItemMeta);
		player.getInventory().setItem(0, classSelectorItem);


		// Tutorial
		ItemStack tutorialItem = new ItemStack(Material.BOOK);
		ItemMeta meta = tutorialItem.getItemMeta();
		meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Voir le Tutoriel");
		meta.setLore(Collections.singletonList(ChatColor.GRAY + "Assistez à un tutoriel interactif !"));
		tutorialItem.setItemMeta(meta);
		GlowEffect.addGlow(tutorialItem);
		player.getInventory().setItem(4, tutorialItem);

		// Leave item
		player.getInventory().setItem(8, coherenceMachine.getLeaveItem());


		player.updateInventory();
		player.getInventory().setHeldItemSlot(0);
	}

	/**
	 * @return The current global damages multiplier of the game.
	 */
	public int getDamagesMultiplicator()
	{
		return damagesMultiplicator;
	}

	/**
	 * Sets the global damages multiplier of the game.
	 *
	 * @param damagesMultiplicator
	 */
	public void setDamagesMultiplicator(int damagesMultiplicator)
	{
		this.damagesMultiplicator = damagesMultiplicator;
	}

	/**
	 * Attaches parameters to an entity.
	 *
	 * @param id
	 * @param params
	 */
	public void addEntityParameters(UUID id, TripleParameters params)
	{
		entitiesData.put(id, params);
	}

	/**
	 * Get parameters for an entity. Also removes the parameters from the map.
	 *
	 * @param id
	 *
	 * @return
	 */
	public TripleParameters getParameters(UUID id)
	{
		TripleParameters params = entitiesData.get(id);
		entitiesData.remove(id);
		return params;
	}
}
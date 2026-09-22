package com.swarmer;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Swarmer",
	description = "Hides scarab swarms at Kephri as soon as they're dead, and shows their wave numbers",
	tags = {"toa", "tombs", "amascut", "kephri", "swarm", "scarab", "death", "indicator", "hide", "party"}
)
public class SwarmerPlugin extends Plugin
{
	private static final int KEPHRI_REGION = 14164;
	private static final int ANIMATION_KEPHRI_DOWN = 9579;
	private static final int ANIMATION_KEPHRI_UP = 9581;
	static final int ANIMATION_SWARM_LEAK = 9607;
	static final int ANIMATION_SWARM_DEATH = 9608;
	private static final int SWARM_BASE_HP = 10;
	// A hidden swarm that still has health this many ticks later was predicted wrong, so it's shown again
	private static final int HIDDEN_TIMEOUT_TICKS = 5;
	private static final String ROOM_FAIL_MESSAGE = "Your party failed to complete";

	private static final Set<Integer> CHINCHOMPAS = ImmutableSet.of(
		ItemID.CHINCHOMPA_CAPTURED, ItemID.CHINCHOMPA_BIG_CAPTURED, ItemID.CHINCHOMPA_BLACK);

	// Hit more than one target, so their XP can't be split between swarms
	private static final Set<Integer> UNSPLITTABLE_WEAPONS = ImmutableSet.of(
		ItemID.SCYTHE_OF_VITUR, ItemID.SCYTHE_OF_VITUR_UNCHARGED,
		ItemID.SCYTHE_OF_VITUR_OR, ItemID.SCYTHE_OF_VITUR_UNCHARGED_OR,
		ItemID.SCYTHE_OF_VITUR_BL, ItemID.SCYTHE_OF_VITUR_UNCHARGED_BL,
		ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR, ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR_UNCHARGED,
		ItemID.DINHS_BULWARK,
		ItemID.VENATOR_BOW, ItemID.VENATOR_BOW_UNCHARGED,
		ItemID.VENATOR_BOW_ORNAMENT, ItemID.VENATOR_BOW_ORNAMENT_UNCHARGED);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Hooks hooks;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SwarmerOverlay overlay;

	@Inject
	private SwarmerConfig config;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	// Swarms spawned since Kephri went down, by NPC index
	private final Map<Integer, Swarm> swarms = new HashMap<>();
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	private boolean inRoom;
	private boolean kephriDowned;
	private int downs;
	private int wave;
	private int lastSpawnTick;
	private int hitpointsXp;
	private int swarmHp;

	// Read on every draw call, so cached instead of going through the config proxy
	private boolean hideKilled;
	private boolean partySync;
	private boolean hideHighSwarms;
	private boolean hideHighNumbers;
	private int phase1Threshold;
	private int phase2Threshold;

	@Provides
	SwarmerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SwarmerConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadConfig();
		overlay.loadFont();
		reset();
		hitpointsXp = -1;
		inRoom = false;
		wsClient.registerMessage(SwarmDamagedMessage.class);
		hooks.registerRenderableDrawListener(drawListener);
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				hitpointsXp = client.getSkillExperience(Skill.HITPOINTS);
				inRoom = isInKephriRoom();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		hooks.unregisterRenderableDrawListener(drawListener);
		wsClient.unregisterMessage(SwarmDamagedMessage.class);
		clientThread.invoke(this::reset);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SwarmerConfig.GROUP.equals(event.getGroup()))
		{
			loadConfig();
			overlay.loadFont();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			hitpointsXp = -1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean now = isInKephriRoom();
		if (now != inRoom)
		{
			inRoom = now;
			reset();
			return;
		}

		for (Swarm swarm : swarms.values())
		{
			if (!swarm.isKilled())
			{
				continue;
			}

			swarm.setHiddenTicks(swarm.getHiddenTicks() + 1);
			if (swarm.getHiddenTicks() > HIDDEN_TIMEOUT_TICKS && swarm.getNpc().getHealthRatio() != 0)
			{
				// Not dead after all, most likely damage we couldn't see or a wrong hitpoints estimate
				swarm.setHiddenTicks(-1);
				swarm.setQueuedDamage(0);
				swarm.getNpc().setDead(false);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (inRoom && event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().startsWith(ROOM_FAIL_MESSAGE))
		{
			reset();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!inRoom || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		if (npc.getId() != NpcID.TOA_KEPHRI_BOSS_SHIELDED && npc.getId() != NpcID.TOA_KEPHRI_BOSS_WEAK)
		{
			return;
		}

		if (!kephriDowned && npc.getAnimation() == ANIMATION_KEPHRI_DOWN)
		{
			kephriDowned = true;
			downs++;
			wave = 0;
			lastSpawnTick = -1;
			swarmHp = calculateSwarmHp();
		}
		else if (kephriDowned && npc.getAnimation() == ANIMATION_KEPHRI_UP)
		{
			kephriDowned = false;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (!inRoom || !kephriDowned || npc.getId() != NpcID.TOA_KEPHRI_SHIELD_SCARAB)
		{
			return;
		}

		// Every swarm spawned on the same tick belongs to the same wave
		int tick = client.getTickCount();
		if (tick != lastSpawnTick)
		{
			wave++;
			lastSpawnTick = tick;
		}

		swarms.put(npc.getIndex(), new Swarm(npc, wave, swarmHp));
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		Swarm swarm = getSwarm(event.getNpc());
		if (swarm != null)
		{
			swarms.remove(swarm.getNpc().getIndex());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		Swarm swarm = getSwarm((NPC) event.getActor());
		if (swarm == null)
		{
			return;
		}

		// Landed hits from anyone, including players without the plugin, count towards the swarm's health
		int damage = event.getHitsplat().getAmount();
		swarm.setHp(swarm.getHp() - damage);
		swarm.setQueuedDamage(Math.max(0, swarm.getQueuedDamage() - damage));
		checkKilled(swarm);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}

		int previous = hitpointsXp;
		hitpointsXp = event.getXp();
		if (previous >= 0 && hitpointsXp > previous)
		{
			processXpDrop(hitpointsXp - previous);
		}
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		// Sent instead of a stat change once Hitpoints is at 200m XP
		if (event.getSkill() == Skill.HITPOINTS)
		{
			processXpDrop(event.getXp());
		}
	}

	/**
	 * Hitpoints XP is given for damage dealt with every combat style, at 4/3 XP per damage,
	 * so unlike the attack style's own XP it doesn't need a table of weapons and spells.
	 * Misses and splashes give none.
	 */
	private void processXpDrop(int xp)
	{
		if (swarms.isEmpty())
		{
			return;
		}

		Player player = client.getLocalPlayer();
		Actor target = player == null ? null : player.getInteracting();
		Swarm swarm = target instanceof NPC ? getSwarm((NPC) target) : null;
		PlayerComposition composition = player == null ? null : player.getPlayerComposition();
		if (swarm == null || composition == null)
		{
			return;
		}

		int weapon = composition.getEquipmentId(KitType.WEAPON);
		if (UNSPLITTABLE_WEAPONS.contains(weapon))
		{
			return;
		}

		// XP is a whole number that can round either way, so take the lowest damage it could be
		int damage = (3 * xp + 1) / 4;
		int animation = player.getAnimation();
		boolean multiTarget = CHINCHOMPAS.contains(weapon)
			|| animation == AnimationID.ZAROS_VERTICAL_CASTING
			|| animation == AnimationID.ZAROS_VERTICAL_CASTING_WALKMERGE;

		if (multiTarget)
		{
			handleAreaAttack(swarm, damage);
		}
		else
		{
			sendDamage(swarm, damage);
		}
	}

	/**
	 * Chinchompas and burst or barrage spells hit everything around the target and the XP is the total.
	 * It can't be split between them, so the swarms are only counted dead if it was enough for all of them.
	 */
	private void handleAreaAttack(Swarm target, int damage)
	{
		WorldPoint centre = target.getNpc().getWorldLocation();
		List<Swarm> clump = new ArrayList<>();
		int clumpHp = 0;
		for (Swarm swarm : swarms.values())
		{
			if (!swarm.isKilled() && swarm.getNpc().getWorldLocation().distanceTo(centre) <= 1)
			{
				clump.add(swarm);
				clumpHp += swarm.getRemainingHp();
			}
		}

		if (clumpHp > damage)
		{
			return;
		}

		for (Swarm swarm : clump)
		{
			sendDamage(swarm, swarm.getRemainingHp());
		}
	}

	private void sendDamage(Swarm swarm, int damage)
	{
		if (damage <= 0)
		{
			return;
		}

		if (partySync && partyService.isInParty())
		{
			partyService.send(new SwarmDamagedMessage(swarm.getNpc().getIndex(), swarm.getWave(), damage));
		}
		queueDamage(swarm, damage);
	}

	@Subscribe
	public void onSwarmDamagedMessage(SwarmDamagedMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (!partySync || (local != null && local.getMemberId() == message.getMemberId()))
		{
			return;
		}

		clientThread.invoke(() ->
		{
			// Only trust it if it matches a swarm from the same wave in this room
			Swarm swarm = swarms.get(message.getNpcIndex());
			if (swarm != null && swarm.getWave() == message.getWave())
			{
				queueDamage(swarm, message.getDamage());
			}
		});
	}

	private void queueDamage(Swarm swarm, int damage)
	{
		swarm.setQueuedDamage(swarm.getQueuedDamage() + damage);
		checkKilled(swarm);
	}

	private void checkKilled(Swarm swarm)
	{
		if (!swarm.isKilled() && swarm.getRemainingHp() <= 0)
		{
			swarm.setHiddenTicks(0);
			if (hideKilled)
			{
				// Lets other plugins, like NPC indicators, treat it as dead too
				swarm.getNpc().setDead(true);
			}
		}
	}

	private Swarm getSwarm(NPC npc)
	{
		Swarm swarm = swarms.get(npc.getIndex());
		return swarm != null && swarm.getNpc() == npc ? swarm : null;
	}

	Collection<Swarm> getSwarms()
	{
		return swarms.values();
	}

	boolean isHidden(Swarm swarm)
	{
		return (hideKilled && swarm.isKilled()) || (hideHighSwarms && isHighWave(swarm));
	}

	boolean isNumberHidden(Swarm swarm)
	{
		return isHidden(swarm) || (hideHighNumbers && isHighWave(swarm));
	}

	private boolean isHighWave(Swarm swarm)
	{
		return swarm.getWave() > (downs <= 1 ? phase1Threshold : phase2Threshold);
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (swarms.isEmpty() || !(renderable instanceof NPC))
		{
			return true;
		}

		Swarm swarm = getSwarm((NPC) renderable);
		return swarm == null || !isHidden(swarm);
	}

	/**
	 * Swarm hitpoints scale like other raid NPCs. This matches the Tombs of Amascut plugin's
	 * Akkha shadow formula. Path level and party size may not apply to swarms, but counting them
	 * only makes a swarm tougher, so a living swarm is never hidden because of them.
	 */
	private int calculateSwarmHp()
	{
		int hp = SWARM_BASE_HP;
		hp += hp * (4 * client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL) / 10) / 100;

		int pathLevel = readPathLevel();
		if (pathLevel > 0)
		{
			hp += hp * (3 + 5 * pathLevel) / 100;
		}

		int partySize = 1;
		for (int varbit = VarbitID.TOA_CLIENT_P1; varbit <= VarbitID.TOA_CLIENT_P7; varbit++)
		{
			if (client.getVarbitValue(varbit) != 0)
			{
				partySize++;
			}
		}
		if (partySize >= 2)
		{
			int partyFactor = 9 * (partySize == 3 ? 2 : 1);
			if (partySize >= 4)
			{
				partyFactor += 6 * (partySize - 3);
			}
			hp += hp * partyFactor / 10;
		}
		return hp;
	}

	private int readPathLevel()
	{
		Widget widget = client.getWidget(InterfaceID.ToaHud.PATHS_LEVEL);
		if (widget == null)
		{
			return 0;
		}

		try
		{
			return Integer.parseInt(widget.getText().trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private boolean isInKephriRoom()
	{
		Player player = client.getLocalPlayer();
		return player != null
			&& WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID() == KEPHRI_REGION;
	}

	private void loadConfig()
	{
		hideKilled = config.hideKilled();
		partySync = config.partySync();
		hideHighSwarms = config.hideHighSwarms();
		hideHighNumbers = config.hideHighNumbers();
		phase1Threshold = config.phase1Threshold();
		phase2Threshold = config.phase2Threshold();
	}

	private void reset()
	{
		for (Swarm swarm : swarms.values())
		{
			if (swarm.isKilled())
			{
				swarm.getNpc().setDead(false);
			}
		}
		swarms.clear();
		kephriDowned = false;
		downs = 0;
		wave = 0;
		lastSpawnTick = -1;
		swarmHp = Integer.MAX_VALUE;
	}
}

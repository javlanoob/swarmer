package com.swarmer;

import com.google.inject.Provides;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
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
	description = "Hides killed and late wave scarab swarms at Kephri and shows their wave numbers",
	tags = {"toa", "tombs", "amascut", "kephri", "swarm", "scarab", "hide", "party"}
)
public class SwarmerPlugin extends Plugin
{
	private static final int KEPHRI_REGION = 14164;
	private static final int ANIMATION_KEPHRI_DOWN = 9579;
	private static final int ANIMATION_KEPHRI_UP = 9581;
	static final int ANIMATION_SWARM_LEAK = 9607;
	static final int ANIMATION_SWARM_DEATH = 9608;
	private static final int ANIMATION_MULTI_TARGET_SPELL = 1979;
	private static final int SWARM_BASE_HP = 10;
	private static final String ROOM_FAIL_MESSAGE = "Your party failed to complete";

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

	// Swarms spawned during the current down, by NPC index
	private final Map<Integer, Swarm> swarms = new HashMap<>();
	// Swarms killed by you or a party member that haven't despawned yet
	private final Set<Integer> killed = new HashSet<>();
	// Damage you've dealt to each swarm, worked out from Hitpoints XP
	private final Map<Integer, Integer> damageDealt = new HashMap<>();
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
		wsClient.registerMessage(SwarmKilledMessage.class);
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
		wsClient.unregisterMessage(SwarmKilledMessage.class);
		reset();
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

		swarms.put(npc.getIndex(), new Swarm(npc, wave));
		killed.remove(npc.getIndex());
		damageDealt.remove(npc.getIndex());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		Swarm swarm = swarms.get(npc.getIndex());
		if (swarm != null && swarm.getNpc() == npc)
		{
			swarms.remove(npc.getIndex());
			killed.remove(npc.getIndex());
			damageDealt.remove(npc.getIndex());
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Hitpoints XP is only given for damage dealt, so splashes and misses don't count
		if (event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}

		int previous = hitpointsXp;
		hitpointsXp = event.getXp();
		if (previous < 0 || hitpointsXp <= previous || swarms.isEmpty())
		{
			return;
		}

		Player player = client.getLocalPlayer();
		Actor target = player == null ? null : player.getInteracting();
		if (!(target instanceof NPC) || isMultiTargetAttack(player))
		{
			return;
		}

		Swarm swarm = swarms.get(((NPC) target).getIndex());
		if (swarm == null || swarm.getNpc() != target)
		{
			return;
		}

		// Hitpoints XP is 4/3 per damage. Integer XP can round either way, so take the lowest damage it could be
		int index = swarm.getNpc().getIndex();
		int damage = damageDealt.merge(index, (3 * (hitpointsXp - previous) + 1) / 4, Integer::sum);
		if (damage < swarmHp || !killed.add(index))
		{
			return;
		}

		if (partySync && partyService.isInParty())
		{
			partyService.send(new SwarmKilledMessage(swarm.getNpc().getIndex(), swarm.getWave()));
		}
	}

	@Subscribe
	public void onSwarmKilledMessage(SwarmKilledMessage message)
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
				killed.add(message.getNpcIndex());
			}
		});
	}

	Collection<Swarm> getSwarms()
	{
		return swarms.values();
	}

	boolean isHidden(Swarm swarm)
	{
		return (hideKilled && killed.contains(swarm.getNpc().getIndex()))
			|| (hideHighSwarms && isHighWave(swarm));
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

		NPC npc = (NPC) renderable;
		Swarm swarm = swarms.get(npc.getIndex());
		return swarm == null || swarm.getNpc() != npc || !isHidden(swarm);
	}

	/**
	 * Chinchompas and burst or barrage spells split their XP between several targets,
	 * so the XP can't be credited to the swarm you clicked.
	 */
	private boolean isMultiTargetAttack(Player player)
	{
		if (player.getAnimation() == ANIMATION_MULTI_TARGET_SPELL)
		{
			return true;
		}

		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		int weaponId = weapon == null ? -1 : weapon.getId();
		return weaponId == ItemID.CHINCHOMPA_CAPTURED || weaponId == ItemID.CHINCHOMPA_BIG_CAPTURED
			|| weaponId == ItemID.CHINCHOMPA_BLACK;
	}

	/**
	 * Swarm hitpoints scale like other raid NPCs. This matches the Tombs of Amascut plugin's
	 * Akkha shadow formula. Path level and party size may not apply to swarms, but counting them
	 * can only make a swarm tougher, so a living swarm is never hidden because of them.
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
		swarms.clear();
		killed.clear();
		damageDealt.clear();
		kephriDowned = false;
		downs = 0;
		wave = 0;
		lastSpawnTick = -1;
		swarmHp = Integer.MAX_VALUE;
	}
}

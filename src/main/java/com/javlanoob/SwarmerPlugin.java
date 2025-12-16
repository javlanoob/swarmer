package com.javlanoob;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.ArrayUtils;
import javax.inject.Inject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PluginDescriptor(
	name = "Swarmer",
	description = "Hides scarab swarms when hit",
	tags = {"toa", "swarm", "scarab"}
)
public class SwarmerPlugin extends Plugin
{
	private static final int SCARAB_SWARM_ID = 11723;
	private static final int TOA_REGION_ID = 14164;

	@Inject
	@Getter
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Hooks hooks;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SwarmerOverlay swarmerOverlay;

	@Inject
	private SwarmerConfig config;

	@Getter
	private boolean inToaRegion = false;

	@Getter
	private final Map<Integer, Scarab> aliveSwarms = new HashMap<>();

	private final Set<Integer> deadSwarmIndexes = new HashSet<>();

	private final Map<Skill, Integer> previousXpMap = new EnumMap<>(Skill.class);

	@Getter
	private int waveNumber = 0;

	private int lastSpawnTick = -1;

	@Getter
	private int currentPhase = 1;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Provides
	SwarmerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SwarmerConfig.class);
	}

	@Override
	protected void startUp()
	{
		clientThread.invoke(this::initializePreviousXpMap);
		hooks.registerRenderableDrawListener(drawListener);
		overlayManager.add(swarmerOverlay);
		inToaRegion = isInToa();
		if (inToaRegion)
		{
			scanForExistingSwarms();
		}
	}

	@Override
	protected void shutDown()
	{
		hooks.unregisterRenderableDrawListener(drawListener);
		overlayManager.remove(swarmerOverlay);
		cleanup();
	}

	/**
	 * Filters out dead swarms and high wave swarms from rendering.
	 * This method is called by the Hooks.RenderableDrawListener.
	 */
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			int npcIndex = npc.getIndex();
			
			// Don't draw dead swarms
			if (deadSwarmIndexes.contains(npcIndex))
			{
				return false;
			}
			
			// Check if we should hide high wave swarms
			if (config.hideHighSwarm())
			{
				Scarab swarm = aliveSwarms.get(npcIndex);
				if (swarm != null && swarm.getWaveSpawned() > getCurrentThreshold())
				{
					return false;
				}
			}
		}
		return true;
	}

	private void initializePreviousXpMap()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		for (Skill skill : Skill.values())
		{
			previousXpMap.put(skill, client.getSkillExperience(skill));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			cleanup();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean wasInToa = inToaRegion;
		inToaRegion = isInToa();

		if (!wasInToa && inToaRegion)
		{
			scanForExistingSwarms();
			initializePreviousXpMap();
		}
		else if (wasInToa && !inToaRegion)
		{
			cleanup();
		}

		// Reset wave counter and cycle phases when all swarms are cleared
		if (inToaRegion && aliveSwarms.isEmpty() && deadSwarmIndexes.isEmpty() && waveNumber > 0)
		{
			waveNumber = 0;
			// Cycle between phase 1 and phase 2
			if (currentPhase == 1)
			{
				currentPhase = 2;
			}
			else
			{
				currentPhase = 1;
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!inToaRegion || aliveSwarms.isEmpty())
		{
			return;
		}

		Skill skill = event.getSkill();
		int xpAfter = client.getSkillExperience(skill);
		Integer xpBeforeObj = previousXpMap.get(skill);
		
		// If this is the first time seeing this skill, initialize it and allow the XP drop to process
		if (xpBeforeObj == null)
		{
			previousXpMap.put(skill, xpAfter);
			// Process this XP drop even though it's the first one we've seen
		}
		else
		{
			int xpBefore = xpBeforeObj;
			previousXpMap.put(skill, xpAfter);
			
			if (xpAfter <= xpBefore)
			{
				return;
			}
		}

		// XP gained - hide the swarm we're currently attacking
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		Actor interacting = player.getInteracting();
		if (!(interacting instanceof NPC))
		{
			return;
		}

		NPC interactingNpc = (NPC) interacting;
		if (interactingNpc.getId() != SCARAB_SWARM_ID)
		{
			return;
		}

		int npcIndex = interactingNpc.getIndex();
		Scarab scarab = aliveSwarms.get(npcIndex);
		
		if (scarab != null && !deadSwarmIndexes.contains(npcIndex))
		{
			// Add to dead list first so shouldDraw filters it immediately
			deadSwarmIndexes.add(npcIndex);
			
			// Set the NPC as dead using the live interacting NPC reference
			if (!interactingNpc.isDead())
			{
				interactingNpc.setDead(true);
			}
			
			aliveSwarms.remove(npcIndex);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!inToaRegion)
		{
			return;
		}

		NPC npc = event.getNpc();
		if (npc.getId() == SCARAB_SWARM_ID)
		{
			int currentTick = client.getTickCount();
			// Increment wave number when new swarms spawn on a different tick
			if (currentTick != lastSpawnTick)
			{
				waveNumber++;
				lastSpawnTick = currentTick;
			}

			aliveSwarms.put(npc.getIndex(), new Scarab(npc, waveNumber));
			
			// If this is a high wave swarm and we're hiding them, mark it as dead immediately
			if (config.hideHighSwarm() && waveNumber > getCurrentThreshold())
			{
				deadSwarmIndexes.add(npc.getIndex());
				// Set the NPC as dead immediately to hide it from all plugins
				if (!npc.isDead())
				{
					npc.setDead(true);
				}
				aliveSwarms.remove(npc.getIndex());
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (!inToaRegion)
		{
			return;
		}

		NPC npc = event.getNpc();
		if (npc.getId() == SCARAB_SWARM_ID)
		{
			aliveSwarms.remove(npc.getIndex());
			deadSwarmIndexes.remove(npc.getIndex());
		}
	}

	public boolean isHidden(NPC npc)
	{
		if (npc.getId() != SCARAB_SWARM_ID)
		{
			return false;
		}

		if (deadSwarmIndexes.contains(npc.getIndex()))
		{
			return true;
		}

		if (config.hideHighSwarm())
		{
			Scarab swarm = aliveSwarms.get(npc.getIndex());
			if (swarm != null && swarm.getWaveSpawned() > getCurrentThreshold())
			{
				return true;
			}
		}

		return false;
	}

	private void cleanup()
	{
		aliveSwarms.clear();
		deadSwarmIndexes.clear();
		previousXpMap.clear();
		waveNumber = 0;
		lastSpawnTick = -1;
		currentPhase = 1;
	}

	private int getCurrentThreshold()
	{
		return currentPhase == 1 ? config.waveThreshold() : config.waveThresholdPhase2();
	}

	private boolean isInToa()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return false;
		}

		WorldPoint worldPoint = localPlayer.getWorldLocation();
		if (worldPoint == null)
		{
			return false;
		}

		int[] mapRegions = client.getMapRegions();
		return ArrayUtils.contains(mapRegions, TOA_REGION_ID);
	}

	private void scanForExistingSwarms()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getId() == SCARAB_SWARM_ID)
			{
				if (!aliveSwarms.containsKey(npc.getIndex()))
				{
					aliveSwarms.put(npc.getIndex(), new Scarab(npc, waveNumber));
				}
			}
		}
	}
}

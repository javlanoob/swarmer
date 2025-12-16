package com.javlanoob;

import lombok.Data;
import net.runelite.api.NPC;

/**
 * Represents a scarab swarm in ToA
 */
@Data
public class Scarab
{
	private final NPC npc;
	private final int waveSpawned;
}

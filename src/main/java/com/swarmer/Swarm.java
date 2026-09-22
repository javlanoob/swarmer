package com.swarmer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.NPC;

@Getter
@Setter(AccessLevel.PACKAGE)
class Swarm
{
	private final NPC npc;
	private final int wave;
	// Ticks since the swarm was hidden as dead, or -1 while it isn't
	private int hiddenTicks = -1;

	Swarm(NPC npc, int wave)
	{
		this.npc = npc;
		this.wave = wave;
	}

	boolean isKilled()
	{
		return hiddenTicks >= 0;
	}
}

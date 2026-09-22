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
	// Hitpoints left after every hitsplat that has landed
	private int hp;
	// Damage from attacks whose hitsplats haven't landed yet
	private int queuedDamage;
	// Ticks since the swarm was hidden as dead, or -1 while it isn't
	private int hiddenTicks = -1;

	Swarm(NPC npc, int wave, int hp)
	{
		this.npc = npc;
		this.wave = wave;
		this.hp = hp;
	}

	int getRemainingHp()
	{
		return hp - queuedDamage;
	}

	boolean isKilled()
	{
		return hiddenTicks >= 0;
	}
}

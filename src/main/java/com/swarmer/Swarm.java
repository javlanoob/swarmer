package com.swarmer;

import lombok.Value;
import net.runelite.api.NPC;

@Value
class Swarm
{
	NPC npc;
	int wave;
}

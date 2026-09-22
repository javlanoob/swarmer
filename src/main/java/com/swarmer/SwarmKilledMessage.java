package com.swarmer;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A swarm a party member has hit, sent before the hitsplat lands. The wave is sent along with
 * the NPC index so a party member in another raid can't hide an unrelated swarm.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class SwarmKilledMessage extends PartyMemberMessage
{
	@SerializedName("i")
	int npcIndex;
	@SerializedName("w")
	int wave;
}

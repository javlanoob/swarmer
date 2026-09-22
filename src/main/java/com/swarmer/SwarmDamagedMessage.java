package com.swarmer;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Damage a party member has dealt to a swarm, sent before the hitsplat lands. The wave is sent
 * along with the NPC index so a party member in another raid can't affect an unrelated swarm.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class SwarmDamagedMessage extends PartyMemberMessage
{
	@SerializedName("i")
	int npcIndex;
	@SerializedName("w")
	int wave;
	@SerializedName("d")
	int damage;
}

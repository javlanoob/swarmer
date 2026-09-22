package com.swarmer;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Sent to the party when a swarm is killed. The wave is sent along with the NPC index
 * so a party member in another raid can't hide an unrelated swarm.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class SwarmKilledMessage extends PartyMemberMessage
{
	int npcIndex;
	int wave;
}

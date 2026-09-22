package com.swarmer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SwarmerConfig.GROUP)
public interface SwarmerConfig extends Config
{
	String GROUP = "swarmer";

	@ConfigSection(
		name = "High waves",
		description = "Swarms from late waves that can't reach Kephri in time",
		position = 10
	)
	String highWaveSection = "highWave";

	@ConfigSection(
		name = "Wave numbers",
		description = "Wave number shown above each swarm",
		position = 20
	)
	String numberSection = "numbers";

	@ConfigItem(
		keyName = "hideKilled",
		name = "Hide killed swarms",
		description = "Hide a swarm as soon as the damage dealt to it is enough to kill it",
		position = 0
	)
	default boolean hideKilled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "partySync",
		name = "Party sync",
		description = "Share your damage on swarms with party members using this plugin",
		position = 1
	)
	default boolean partySync()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideHighSwarms",
		name = "Hide high wave swarms",
		description = "Hide swarms above the wave threshold",
		position = 11,
		section = highWaveSection
	)
	default boolean hideHighSwarms()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideHighNumbers",
		name = "Hide high wave numbers",
		description = "Hide wave numbers above the wave threshold",
		position = 12,
		section = highWaveSection
	)
	default boolean hideHighNumbers()
	{
		return false;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "phase1Threshold",
		name = "First down threshold",
		description = "Highest wave shown on Kephri's first down",
		position = 13,
		section = highWaveSection
	)
	default int phase1Threshold()
	{
		return 20;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "phase2Threshold",
		name = "Later downs threshold",
		description = "Highest wave shown on Kephri's later downs",
		position = 14,
		section = highWaveSection
	)
	default int phase2Threshold()
	{
		return 17;
	}

	@ConfigItem(
		keyName = "showNumbers",
		name = "Show wave numbers",
		description = "Show which wave each swarm spawned in",
		position = 21,
		section = numberSection
	)
	default boolean showNumbers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font of the wave numbers",
		position = 22,
		section = numberSection
	)
	default SwarmerFont fontType()
	{
		return SwarmerFont.RUNESCAPE;
	}

	@ConfigItem(
		keyName = "boldFont",
		name = "Bold",
		description = "Use a bold font",
		position = 23,
		section = numberSection
	)
	default boolean boldFont()
	{
		return true;
	}

	@Range(min = 8, max = 40)
	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the wave numbers",
		position = 24,
		section = numberSection
	)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "fontColor",
		name = "Font color",
		description = "Color of the wave numbers",
		position = 25,
		section = numberSection
	)
	default Color fontColor()
	{
		return Color.WHITE;
	}
}

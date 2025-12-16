package com.javlanoob;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import java.awt.Color;

@ConfigGroup("swarmer")
public interface SwarmerConfig extends Config
{
	@ConfigSection(
		name = "High Wave Swarms",
		description = "Settings for hiding high-numbered swarms",
		position = 0
	)
	String highWaveSection = "highWave";

	@ConfigItem(
		keyName = "hideHighSwarm",
		name = "Hide High Wave NPC",
		description = "Hide swarm NPCs above the wave threshold",
		position = 1,
		section = highWaveSection
	)
	default boolean hideHighSwarm()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideHighNumber",
		name = "Hide High Wave Number",
		description = "Hide wave numbers above the threshold",
		position = 2,
		section = highWaveSection
	)
	default boolean hideHighNumber()
	{
		return false;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "waveThreshold",
		name = "Phase 1 Threshold",
		description = "Hide swarms over this wave in phase 1",
		position = 3,
		section = highWaveSection
	)
	default int waveThreshold()
	{
		return 20;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "waveThresholdPhase2",
		name = "Phase 2 Threshold",
		description = "Hide swarms over this wave in phase 2",
		position = 4,
		section = highWaveSection
	)
	default int waveThresholdPhase2()
	{
		return 17;
	}

	@ConfigSection(
		name = "Swarm Overlay",
		description = "Wave number overlay settings",
		position = 10
	)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = "showNumbers",
		name = "Show Wave Numbers",
		description = "Display wave numbers above swarms",
		position = 11,
		section = overlaySection
	)
	default boolean showNumbers()
	{
		return true;
	}

	@Range(min = 10, max = 40)
	@ConfigItem(
		keyName = "fontSize",
		name = "Font Size",
		description = "Size of the wave numbers (default: 16)",
		position = 12,
		section = overlaySection
	)
	default int fontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "fontColor",
		name = "Font Color",
		description = "Color of the wave numbers",
		position = 13,
		section = overlaySection
	)
	default Color swarmerFontColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "swarmerFontType",
		name = "Font Type",
		description = "Font type for wave numbers",
		position = 14,
		section = overlaySection
	)
	default SwarmerFonts swarmerFontType()
	{
		return SwarmerFonts.REGULAR;
	}

	@ConfigItem(
		keyName = "useBoldFont",
		name = "Bold Font",
		description = "Use bold font for wave numbers",
		position = 15,
		section = overlaySection
	)
	default boolean useBoldFont()
	{
		return true;
	}

	@Range(min = 10, max = 40)
	@ConfigItem(
		keyName = "swarmerFontSize",
		name = "Font Size",
		description = "Size of the wave numbers (default: 16)",
		position = 16,
		section = overlaySection
	)
	default int swarmerFontSize()
	{
		return 16;
	}
}

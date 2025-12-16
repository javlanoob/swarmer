package com.javlanoob;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.stream.Collectors;

public class SwarmerOverlay extends Overlay
{
	private final Client client;
	private final SwarmerPlugin plugin;
	private final SwarmerConfig config;

	@Inject
	public SwarmerOverlay(Client client, SwarmerPlugin plugin, SwarmerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPriority(Overlay.PRIORITY_HIGH);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isInToaRegion())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		plugin.getAliveSwarms().values()
			.stream()
			.filter(swarm -> !shouldFilterSwarm(swarm))
			.collect(Collectors.groupingBy(swarm -> swarm.getNpc().getWorldLocation()))
			.values()
			.forEach(tileSwarms ->
			{
				int stackOffset = 0;
				for (Scarab swarm : tileSwarms)
				{
					this.draw(graphics, swarm, stackOffset);
					stackOffset += graphics.getFontMetrics().getHeight();
				}
			});
		return null;
	}

	private boolean shouldFilterSwarm(Scarab swarm)
	{
		// Don't show if the NPC itself is being hidden
		if (plugin.isHidden(swarm.getNpc()))
		{
			return true;
		}
		// Don't show if hiding high wave numbers and this swarm is above phase-specific threshold
		if (config.hideHighNumber())
		{
			int threshold = plugin.getCurrentPhase() == 1 ? config.waveThreshold() : config.waveThresholdPhase2();
			if (swarm.getWaveSpawned() > threshold)
			{
				return true;
			}
		}
		return false;
	}

	private void draw(Graphics2D graphics, Scarab swarmer, int offset)
	{
		String text = String.valueOf(swarmer.getWaveSpawned());

		Point canvasTextLocation = swarmer.getNpc().getCanvasTextLocation(graphics, text, 0);
		if (canvasTextLocation == null)
		{
			return;
		}
		int x = canvasTextLocation.getX();
		int y = canvasTextLocation.getY() + offset;

		Font font;
		if (config.swarmerFontType() == SwarmerFonts.REGULAR)
		{
			font = config.useBoldFont() 
				? FontManager.getRunescapeBoldFont().deriveFont((float) config.swarmerFontSize())
				: FontManager.getRunescapeFont().deriveFont((float) config.swarmerFontSize());
		}
		else
		{
			font = new Font(config.swarmerFontType().toString(), config.useBoldFont() ? Font.BOLD : Font.PLAIN, config.swarmerFontSize());
		}
		FontRenderContext frc = graphics.getFontRenderContext();
		TextLayout tl = new TextLayout(text, font, frc);
		Shape outline = tl.getOutline(null);
		graphics.translate(x, y);
		graphics.setStroke(new BasicStroke(3));

		graphics.setColor(Color.BLACK);
		graphics.draw(outline);
		graphics.setColor(config.swarmerFontColor());
		graphics.fill(outline);
		graphics.translate(-x, -y);
	}
}

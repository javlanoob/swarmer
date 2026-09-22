package com.swarmer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextLayout;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class SwarmerOverlay extends Overlay
{
	private static final Stroke OUTLINE = new BasicStroke(3);

	private final SwarmerPlugin plugin;
	private final SwarmerConfig config;
	private final Map<WorldPoint, Integer> tileOffsets = new HashMap<>();

	private volatile Font font;

	@Inject
	SwarmerOverlay(SwarmerPlugin plugin, SwarmerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	void loadFont()
	{
		float size = config.fontSize();
		if (config.fontType() == SwarmerFont.RUNESCAPE)
		{
			font = (config.boldFont() ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeFont()).deriveFont(size);
		}
		else
		{
			font = new Font(config.fontType().getName(), config.boldFont() ? Font.BOLD : Font.PLAIN, (int) size);
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showNumbers() || plugin.getSwarms().isEmpty())
		{
			return null;
		}

		graphics.setFont(font);
		graphics.setStroke(OUTLINE);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Color color = config.fontColor();
		int lineHeight = graphics.getFontMetrics().getHeight();

		// Swarms sharing a tile get their numbers stacked instead of drawn on top of each other
		tileOffsets.clear();
		for (Swarm swarm : plugin.getSwarms())
		{
			NPC npc = swarm.getNpc();
			int animation = npc.getAnimation();
			if (animation == SwarmerPlugin.ANIMATION_SWARM_DEATH || animation == SwarmerPlugin.ANIMATION_SWARM_LEAK
				|| plugin.isNumberHidden(swarm))
			{
				continue;
			}

			String text = Integer.toString(swarm.getWave());
			Point location = npc.getCanvasTextLocation(graphics, text, 0);
			if (location == null)
			{
				continue;
			}

			int offset = tileOffsets.merge(npc.getWorldLocation(), lineHeight, Integer::sum) - lineHeight;
			Shape outline = new TextLayout(text, font, graphics.getFontRenderContext())
				.getOutline(null);

			int x = location.getX();
			int y = location.getY() + offset;
			graphics.translate(x, y);
			graphics.setColor(Color.BLACK);
			graphics.draw(outline);
			graphics.setColor(color);
			graphics.fill(outline);
			graphics.translate(-x, -y);
		}
		return null;
	}
}

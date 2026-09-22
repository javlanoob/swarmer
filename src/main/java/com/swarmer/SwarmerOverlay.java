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
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class SwarmerOverlay extends Overlay
{
	private static final Stroke OUTLINE = new BasicStroke(3);

	private final Client client;
	private final SwarmerPlugin plugin;
	private final SwarmerConfig config;
	private final ModelOutlineRenderer outlineRenderer;
	private final List<Swarm> sorted = new ArrayList<>();
	private final List<Rectangle> drawn = new ArrayList<>();

	private volatile Font font;

	@Inject
	SwarmerOverlay(Client client, SwarmerPlugin plugin, SwarmerConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
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
		if (plugin.getSwarms().isEmpty())
		{
			return null;
		}

		if (config.highlight())
		{
			renderHighlights(graphics);
		}

		if (!config.showNumbers())
		{
			return null;
		}

		graphics.setFont(font);
		graphics.setStroke(OUTLINE);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Color color = config.fontColor();

		// Numbers that would overlap one already drawn are moved down below it. Going in a fixed
		// order keeps each number in the same place from frame to frame.
		sorted.clear();
		sorted.addAll(plugin.getSwarms());
		sorted.sort(Comparator.comparingInt(Swarm::getWave).thenComparingInt(swarm -> swarm.getNpc().getIndex()));
		drawn.clear();
		for (Swarm swarm : sorted)
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

			Shape outline = new TextLayout(text, font, graphics.getFontRenderContext())
				.getOutline(null);
			Rectangle bounds = outline.getBounds();
			bounds.translate(location.getX(), location.getY());
			bounds.grow(1, 1);
			for (int i = 0; i < drawn.size(); i++)
			{
				Rectangle other = drawn.get(i);
				if (bounds.intersects(other))
				{
					bounds.y = other.y + other.height;
					// Moving it can make it hit one that was already checked
					i = -1;
				}
			}
			drawn.add(bounds);

			int x = location.getX();
			int y = bounds.y - outline.getBounds().y + 1;
			graphics.translate(x, y);
			graphics.setColor(Color.BLACK);
			graphics.draw(outline);
			graphics.setColor(color);
			graphics.fill(outline);
			graphics.translate(-x, -y);
		}
		return null;
	}

	private void renderHighlights(Graphics2D graphics)
	{
		HighlightMode mode = config.highlightMode();
		Color color = config.highlightColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 5);
		for (Swarm swarm : plugin.getSwarms())
		{
			NPC npc = swarm.getNpc();
			int animation = npc.getAnimation();
			if (animation == SwarmerPlugin.ANIMATION_SWARM_DEATH || animation == SwarmerPlugin.ANIMATION_SWARM_LEAK
				|| plugin.isHighlightHidden(swarm))
			{
				continue;
			}

			Shape shape = null;
			switch (mode)
			{
				case TRUE_TILE:
					// The tile the server has it on, rather than where it's drawn mid-walk
					LocalPoint tile = LocalPoint.fromWorld(npc.getWorldView(), npc.getWorldLocation());
					if (tile != null)
					{
						int size = npc.getComposition().getSize();
						shape = Perspective.getCanvasTileAreaPoly(client, tile.plus(
							(size - 1) * Perspective.LOCAL_HALF_TILE_SIZE, (size - 1) * Perspective.LOCAL_HALF_TILE_SIZE), size);
					}
					break;
				case TILE:
					shape = npc.getCanvasTilePoly();
					break;
				case HULL:
					shape = npc.getConvexHull();
					break;
				case OUTLINE:
					outlineRenderer.drawOutline(npc, 2, color, 4);
					break;
			}

			if (shape != null)
			{
				OverlayUtil.renderPolygon(graphics, shape, color, fill, new BasicStroke(2));
			}
		}
		graphics.setStroke(OUTLINE);
	}
}

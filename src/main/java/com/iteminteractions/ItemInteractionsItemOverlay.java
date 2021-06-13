package com.iteminteractions;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class ItemInteractionsItemOverlay extends WidgetItemOverlay
{

	private final Client client;
	private final ItemInteractionsPlugin plugin;

	private static final Color PURPLE = new Color(170, 0, 255);

	@Inject
	ItemInteractionsItemOverlay(Client client, ItemInteractionsPlugin plugin, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		showOnInventory();
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!plugin.getIds().getItemIds().contains(itemId))
		{
			return;
		}

		Point location = widgetItem.getCanvasLocation();
		graphics.setColor(PURPLE);
		graphics.drawRect(location.getX() + 26, location.getY() + 22, 6, 6);
	}
}

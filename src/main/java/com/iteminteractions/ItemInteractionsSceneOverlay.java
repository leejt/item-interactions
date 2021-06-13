package com.iteminteractions;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

@Singleton
class ItemInteractionsSceneOverlay extends Overlay
{
	private static final Color PURPLE = new Color(170, 0, 255);

	private static final int MAX_DISTANCE = 2400;

	private final Client client;
	private final ItemInteractionsPlugin plugin;

	@Inject
	private ItemInteractionsSceneOverlay(Client client, ItemInteractionsPlugin plugin)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGHEST);
		this.client = client;
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		renderTileObjects(graphics);
		for (NPC npc : plugin.getCurrentNpcs())
		{
			renderNpc(graphics, npc);
		}
		return null;
	}

	private void renderNpc(Graphics2D graphics, NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		if (!plugin.getIds().getNpcIds().contains(npc.getId()))
		{
			return;
		}

		Shape objectClickbox = npc.getConvexHull();
		if (objectClickbox == null)
		{
			return;
		}

		graphics.setColor(PURPLE);
		graphics.draw(objectClickbox);
		graphics.setColor(new Color(PURPLE.getRed(), PURPLE.getGreen(), PURPLE.getBlue(), 20));
		graphics.fill(objectClickbox);	}

	private void renderTileObjects(Graphics2D graphics)
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();

		int z = client.getPlane();

		for (int x = 0; x < Constants.SCENE_SIZE; ++x)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; ++y)
			{
				Tile tile = tiles[z][x][y];

				if (tile == null)
				{
					continue;
				}

				Player player = client.getLocalPlayer();
				if (player == null)
				{
					continue;
				}

				renderObject(graphics, tile.getGroundObject(), player);
				renderGameObjects(graphics, tile, player);
				renderObject(graphics, tile.getWallObject(), player);
				renderObject(graphics, tile.getDecorativeObject(), player);

			}
		}
	}

	private void renderGameObjects(Graphics2D graphics, Tile tile, Player player)
	{
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				// Checks whether it's a loc
				if (gameObject != null && (gameObject.getHash() >>> 14 & 3L) == 2
					&& gameObject.getSceneMinLocation().equals(tile.getSceneLocation()))
				{
					renderObject(graphics, gameObject, player);
				}
			}
		}
	}

	private void renderObject(Graphics2D graphics, TileObject tileObject, Player player)
	{
		if (tileObject != null)
		{
			if (plugin.getIds().getObjectIds().contains(tileObject.getId()) && ((tileObject.getHash() >>> 16)  & 1) == 0)
			{
				if (player.getLocalLocation().distanceTo(tileObject.getLocalLocation()) <= MAX_DISTANCE)
				{
					Shape objectClickbox = tileObject.getClickbox();
					if (objectClickbox != null)
					{
						graphics.setColor(PURPLE);
						graphics.draw(objectClickbox);
						graphics.setColor(new Color(PURPLE.getRed(), PURPLE.getGreen(), PURPLE.getBlue(), 20));
						graphics.fill(objectClickbox);
						OverlayUtil.renderTileOverlay(graphics, tileObject, "ID: " + tileObject.getId(), PURPLE);
					}
				}
			}
		}
	}
}

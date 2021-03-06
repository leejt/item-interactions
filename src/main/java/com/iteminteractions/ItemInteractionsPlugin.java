package com.iteminteractions;

import com.google.common.collect.EvictingQueue;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Item Interactions Hunt",
	description = "Help the wiki discover unknown item interactions by using your items on everything you see!"
)
public class ItemInteractionsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ItemInteractionsSceneOverlay sceneOverlay;

	@Inject
	private ItemInteractionsItemOverlay itemOverlay;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private static final String VERSION_NUMBER = "1.2";

	final TypeToken<Map<String, Collection<Integer>>> typeToken = new TypeToken<Map<String, Collection<Integer>>>(){};
	static final String CONFIG_GROUP_KEY = "item-interactions";

	private int firstItem = -1;
	private int secondEntity = -1;
	private MenuAction actionType = null;
	private String menuTarget = null;
	private int lastTick = -1;
	private boolean waitingForNIH = false;

	private int tickOfLastMove = -1;
	private WorldPoint lastPosition = null;

	// To handle the case where they do some, but then the every-minute request is cached from before their work...
	private EvictingQueue<Interaction> recentInteractions = EvictingQueue.create(60);

	// Surely a better Lombok-y way to do this, but don't go crazy if we can't load before startUp
	@Getter
	private IdsData ids = new IdsData(
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>()
	);

	@Getter
	private Set<NPC> currentNpcs = new HashSet<>();

	private static final String FAILED_TO_LOAD = "Failed to load the list of wanted interactions. Retrying in 1 minute.";
	private static final String FAILED_TO_SEND = "Failed to send your most recent submission.";

	private static final String NOTHING_INTERESTING_HAPPENS = "Nothing interesting happens.";
	private static final String CANT_REACH = "I can't reach that!";
	private static final String TOO_FAST = "Too fast!";
	private static final int DELAY_TOLERANCE = 1;
	private static final int MOVEMENT_TOLERANCE = 2;

	private static final String WANTED_URL = "https://chisel.weirdgloop.org/interactions/wanted";
	private static final String SUBMIT_URL = "https://chisel.weirdgloop.org/interactions/submit";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	@Override
	protected void startUp() throws Exception
	{
		getRemoteIds();
		overlayManager.add(sceneOverlay);
		overlayManager.add(itemOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(itemOverlay);
	}

	@Provides
	ItemInteractionsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemInteractionsConfig.class);
	}

	private void sendMessage(String message)
	{
		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(message)
			.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(chatMessage.build())
			.build());
	}

	private void removeRecentInteractions()
	{
		for (Interaction i : recentInteractions)
		{
			switch (i.getType())
			{
				case ITEM_USE_ON_GAME_OBJECT:
					ids.getObjectIds().remove(i.getId());
					break;
				case ITEM_USE_ON_NPC:
					ids.getNpcIds().remove(i.getId());
					break;
				case ITEM_USE_ON_WIDGET_ITEM:
					ids.getItemIds().remove(i.getId());
					break;
			}
		}
	}

	@Schedule(
		period = 60,
		unit = ChronoUnit.SECONDS,
		asynchronous = true
	)
	public void getRemoteIds()
	{
		Request request = new Request.Builder()
			.url(WANTED_URL)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				sendMessage(FAILED_TO_LOAD);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					InputStream in = response.body().byteStream();
					IdsData tmp = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), IdsData.class);
					if (tmp == null)
					{
						sendMessage(FAILED_TO_LOAD);
						response.close();
						return;
					}
					ids = tmp;
					removeRecentInteractions();
					response.close();
				}
				catch (JsonParseException ex)
				{
					sendMessage(FAILED_TO_LOAD);
					response.close();
				}
			}
		});
	}

	public void processNIH(boolean interactable, boolean sawNIH)
	{
		if (actionType == null)
		{
			return;
		}

		Set<Integer> typedIds;
		Set<Integer> unsureTypedIds;

		String type;
		switch (actionType)
		{
			case ITEM_USE_ON_GAME_OBJECT:
				typedIds = ids.getObjectIds();
				unsureTypedIds = ids.getUnsureObjectIds();
				type = "object";
				break;
			case ITEM_USE_ON_NPC:
				typedIds = ids.getNpcIds();
				unsureTypedIds = ids.getUnsureNpcIds();
				type = "npc";
				break;
			case ITEM_USE_ON_WIDGET_ITEM:
				typedIds = ids.getItemIds();
				unsureTypedIds = ids.getUnsureItemIds();

				type = "item";
				break;
			default:
				reset();
				return;
		}
		if (!typedIds.contains(secondEntity))
		{
			reset();
			return;
		}

		if (actionType == MenuAction.ITEM_USE_ON_WIDGET_ITEM && !ids.getAllowedItemIds().contains(firstItem))
		{
			sendMessage("Sorry, only some items work as the first item. Type \"::wiki nih\" for a list.");
			reset();
			return;
		}

		if (sawNIH)
		{
			String addendum = "has no interactions at all.";
			if (interactable)
			{
				addendum = "has interactions!";
			}
			if (menuTarget == null)
			{
				menuTarget = "";
			}
			String readable = Text.removeTags(menuTarget);
			String[] parts = readable.split(" -> ");
			if (parts.length == 2)
			{
				readable = parts[1];
			}

			sendMessage(String.format("%s %s. Sending...", readable, addendum));
			typedIds.remove(secondEntity);
			recentInteractions.add(new Interaction(actionType, secondEntity));
		}
		else
		{
			sendMessage("We didn't see NIH. The object/NPC might have interactions for all items. Sending...");
			unsureTypedIds.add(secondEntity);
		}
		submit(interactable, type, sawNIH);
		reset();
	}

	private void submit(boolean interactable, String type, boolean sawNIH)
	{
		SubmissionData data = new SubmissionData(type, firstItem, secondEntity,
			menuTarget, interactable, client.getLocalPlayer().getName(), sawNIH, VERSION_NUMBER);

		Request request = new Request.Builder()
			.url(SUBMIT_URL)
			.post(RequestBody.create(JSON, gson.toJson(data)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				sendMessage(FAILED_TO_SEND);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					InputStream in = response.body().byteStream();
					SubmissionResponseData payload = gson.fromJson(
						new InputStreamReader(in, StandardCharsets.UTF_8), SubmissionResponseData.class);
					response.close();
					sendMessage(payload.getMessage());
				}
				catch (JsonParseException ex)
				{
					sendMessage(FAILED_TO_SEND);
					response.close();
				}
			}
		});
	}

	private void reset()
	{
		firstItem = -1;
		secondEntity = -1;
		actionType = null;
		menuTarget = null;
		lastTick = -1;
		waitingForNIH = false;

		tickOfLastMove = -1;
		lastPosition = null;

	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (action == MenuAction.ITEM_USE_ON_GAME_OBJECT
			|| action == MenuAction.ITEM_USE_ON_NPC
			|| action == MenuAction.ITEM_USE_ON_WIDGET_ITEM)
		{
			if (client.getTickCount() - lastTick <= DELAY_TOLERANCE || waitingForNIH)
			{
				sendMessage(TOO_FAST);
				lastTick = client.getTickCount();
				reset();
				return;
			}

			ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
			if (container == null)
			{
				return;
			}

			firstItem = container.getItems()[event.getSelectedItemIndex()].getId();
			menuTarget = event.getMenuTarget();
			actionType = action;

			secondEntity = event.getId();
			if (action == MenuAction.ITEM_USE_ON_NPC)
			{
				secondEntity = client.getCachedNPCs()[event.getId()].getComposition().getId();
			}

			waitingForNIH = true;
			lastTick = client.getTickCount();
		}
		else
		{
			if (action == MenuAction.ITEM_USE)
			{
				// This one can be safely ignored, as it never triggers any pathing.
				return;
			}
			if (waitingForNIH)
			{
				sendMessage("Uh oh! You clicked something else before we saw a Nothing interesting happens. Try again?");
				reset();
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!waitingForNIH)
		{
			return;
		}

		ChatMessageType type = event.getType();
		if (type != ChatMessageType.ENGINE && type != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();
		if (message.equals(NOTHING_INTERESTING_HAPPENS))
		{
			processNIH(type == ChatMessageType.GAMEMESSAGE, true);
		}
		else if (message.equals(CANT_REACH))
		{
			reset();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!waitingForNIH)
		{
			return;
		}

		WorldPoint position = client.getLocalPlayer().getWorldLocation();
		if (lastPosition == null || lastPosition.distanceTo(position) != 0)
		{
			tickOfLastMove = client.getTickCount();
			lastPosition = position;
		}
		else
		{
			if (client.getTickCount() - tickOfLastMove >= MOVEMENT_TOLERANCE)
			{
				processNIH(false, false);
			}
		}
	}
}
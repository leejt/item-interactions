package com.iteminteractions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ItemInteractionsPlugin.CONFIG_GROUP_KEY)
public interface ItemInteractionsConfig extends Config
{
	@ConfigItem(
		keyName = "showUnsure",
		position = 0,
		name = "Show unsure entities",
		description = "Marks objects/NPCs/items red if we're not yet sure if they have interactions, but we can't produce a NIH."
	)
	default boolean showUnsure()
	{
		return false;
	}
}
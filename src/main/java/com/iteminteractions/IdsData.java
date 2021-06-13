package com.iteminteractions;

import java.util.Set;
import lombok.Data;

@Data
public class IdsData
{
	private final Set<Integer> objectIds;
	private final Set<Integer> itemIds;
	private final Set<Integer> npcIds;

	private final Set<Integer> allowedItemIds;

}

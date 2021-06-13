package com.iteminteractions;

import lombok.Data;
import net.runelite.api.MenuAction;

@Data
public class Interaction
{
	private final MenuAction type;
	private final int id;

}

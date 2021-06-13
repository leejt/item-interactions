package com.iteminteractions;

import lombok.Data;

@Data
public class SubmissionData
{
	private final String type;
	private final int firstItem;
	private final int id;
	private final String menuTarget;
	private final boolean interactable;
	private final String username;
	private final boolean sawNIH;

}

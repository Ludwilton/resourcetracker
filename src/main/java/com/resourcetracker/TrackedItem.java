package com.resourcetracker;

import lombok.Data;

@Data
public class TrackedItem
{
	private int itemId;
	private String itemName;
	private int currentAmount;
	private Integer goalAmount;
	private String category = "Default";



	public TrackedItem(int itemId, String itemName, Integer goalAmount, String category)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentAmount = 0;
		this.goalAmount = goalAmount;
		this.category = category;
	}

}

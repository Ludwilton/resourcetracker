package com.resourcetracker;

import lombok.Data;

@Data
public class TrackedItem
{
	private int itemId;
	private String itemName;
	private int currentAmount;
	private int goalAmount;
	private String category = "Default";

	public TrackedItem()
	{
		// Default constructor for Gson
	}

	public TrackedItem(int itemId, String itemName, int goalAmount)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentAmount = 0;
		this.goalAmount = goalAmount;
		this.category = "Default";
	}

	public TrackedItem(int itemId, String itemName, int goalAmount, String category)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentAmount = 0;
		this.goalAmount = goalAmount;
		this.category = category;
	}

	public int getRemaining()
	{
		return goalAmount - currentAmount;
	}

	public boolean isComplete()
	{
		return currentAmount >= goalAmount;
	}
}

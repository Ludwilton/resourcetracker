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

	@SuppressWarnings("unused")
	public TrackedItem()
	{
		// Default constructor for Gson
	}

	public TrackedItem(int itemId, String itemName, Integer goalAmount, String category)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentAmount = 0;
		this.goalAmount = goalAmount;
		this.category = category;
	}

	public int getRemaining()
	{
		if (goalAmount == null)
		{
			return 0;
		}
		return Math.max(0, goalAmount - currentAmount);
	}

	public boolean isComplete()
	{
		if (goalAmount == null)
		{
			return false;
		}
		return currentAmount >= goalAmount;
	}
}

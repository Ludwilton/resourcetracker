package com.resourcetracker;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class TrackedItem
{
	private int itemId;
	private String itemName;
	private int startingAmount;
	private int currentAmount;
	private String category;
	private Integer goalAmount;
	private Map<String, Integer> containerQuantities = new HashMap<>();

	// No-argument constructor for Gson deserialization
	public TrackedItem()
	{
		this.containerQuantities = new HashMap<>();
	}

	public TrackedItem(int itemId, String itemName, Integer goalAmount, String category)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.currentAmount = 0;
		this.goalAmount = goalAmount;
		this.category = category;
		this.containerQuantities = new HashMap<>();
	}

	public void setContainerQuantities(Map<String, Integer> containerQuantities)
	{
		this.containerQuantities = (containerQuantities == null) ? new HashMap<>() : containerQuantities;
	}

}

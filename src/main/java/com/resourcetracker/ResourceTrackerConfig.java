package com.resourcetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("resourcetracker")
public interface ResourceTrackerConfig extends Config
{
	@ConfigItem(
		keyName = "trackedItems",
		name = "Tracked Items",
		description = "Serialized data of tracked items (internal use)",
		hidden = true
	)
	default String trackedItems()
	{
		return "";
	}
}

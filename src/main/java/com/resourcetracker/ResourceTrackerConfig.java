package com.resourcetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("resourcetracker")
public interface ResourceTrackerConfig extends Config
{

	@ConfigItem(
		keyName = "trackBank",
		name = "Track Bank",
		description = "Track items in the bank"
	)
	default boolean trackBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackInventory",
		name = "Track Inventory",
		description = "Track items in the inventory"
	)
	default boolean trackInventory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "trackSeedVault",
		name = "Track Seed Vault",
		description = "Track items in the Seed Vault"
	)
	default boolean trackSeedVault()
	{
		return true;
	}
}

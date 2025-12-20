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

	@ConfigItem(
		keyName = "trackGravestone",
		name = "Track Gravestone",
		description = "Track items in your Gravestone"
	)
	default boolean trackGravestone()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackGroupStorage",
		name = "Track Group Storage",
		description = "Track items in Group Storage"
	)
	default boolean trackGroupStorage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackLootingBag",
		name = "Track Looting Bag",
		description = "Track items in your Looting Bag"
	)
	default boolean trackLootingBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackBoatInventory",
		name = "Track Boat Inventory",
		description = "Track items in your Sailing boats"
	)
	default boolean trackBoatInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackPotionStorage",
		name = "Track Potion Storage",
		description = "Track potions in your Potion Storage (Bank tab)"
	)
	default boolean trackPotionStorage()
	{
		return true;
	}
}

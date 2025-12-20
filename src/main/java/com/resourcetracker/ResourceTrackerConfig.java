package com.resourcetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("resourcetracker")
public interface ResourceTrackerConfig extends Config
{
	@ConfigSection(
		name = "Containers",
		description = "Configure which containers to track",
		position = 0
	)
	String containersSection = "containers";

	@ConfigItem(
		keyName = "trackBank",
		name = "Track Bank",
		description = "Track items in the bank",
		section = containersSection
	)
	default boolean trackBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackInventory",
		name = "Track Inventory",
		description = "Track items in the inventory",
		section = containersSection
	)
	default boolean trackInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSeedVault",
		name = "Track Seed Vault",
		description = "Track items in the Seed Vault",
		section = containersSection
	)
	default boolean trackSeedVault()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackRetrievalService",
		name = "Track Retrieval Service",
		description = "Track items in your Retrieval Service / Gravestone",
		section = containersSection
	)
	default boolean trackRetrievalService()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackGroupStorage",
		name = "Track Group Storage",
		description = "Track items in Group Storage",
		section = containersSection
	)
	default boolean trackGroupStorage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackLootingBag",
		name = "Track Looting Bag",
		description = "Track items in your Looting Bag",
		section = containersSection
	)
	default boolean trackLootingBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackBoatInventory",
		name = "Track Boat Inventory",
		description = "Track items in your Sailing boats",
		section = containersSection
	)
	default boolean trackBoatInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackPotionStorage",
		name = "Track Potion Storage",
		description = "Track potions in your Potion Storage",
		section = containersSection
	)
	default boolean trackPotionStorage()
	{
		return true;
	}
    @ConfigItem(
            keyName = "trackPOHStorage",
            name = "Track POH Storage",
            description = "Track items in the Player Owned House storage room",
            section = containersSection
    )
    default boolean trackPOHStorage()
    {
        return true;
    }
}

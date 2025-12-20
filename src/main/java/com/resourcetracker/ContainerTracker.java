package com.resourcetracker;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for tracking item quantities across different containers.
 * Highly modular - add new containers by simply adding them to the CONTAINER_REGISTRY.
 */
public class ContainerTracker
{
	/**
	 * Container definition - stores ID, friendly name, and config key.
	 */
	public static class Container
	{
		private final int id;
		private final String name;
		private final String configKey;

		public Container(int id, String name, String configKey)
		{
			this.id = id;
			this.name = name;
			this.configKey = configKey;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public String getConfigKey()
		{
			return configKey;
		}
	}

	// ===== CONTAINER REGISTRY =====
	// To add a new container: Just add a new line here with (ID, "Display Name", "configKey")
	// Then add the config item to ResourceTrackerConfig.java
	public static final Container BANK = new Container(95, "Bank", "trackBank");
	public static final Container INVENTORY = new Container(93, "Inventory", "trackInventory");
	public static final Container SEED_VAULT = new Container(626, "Seed Vault", "trackSeedVault");
	public static final Container GRAVESTONE = new Container(525, "Gravestone", "trackGravestone");
	public static final Container GROUP_STORAGE = new Container(659, "Group storage", "trackGroupStorage");
	public static final Container LOOTING_BAG = new Container(516, "Looting Bag", "trackLootingBag");
	public static final Container POTION_STORAGE = new Container(-420, "Potion Storage", "trackPotionStorage"); // Fake ID - no real container
	public static final Container BOAT_1 = new Container(963, "Boat 1", "trackBoatInventory");
	public static final Container BOAT_1_ALT = new Container(33731, "Boat 1", "trackBoatInventory"); // Alternate ID
	public static final Container BOAT_2 = new Container(964, "Boat 2", "trackBoatInventory");
	public static final Container BOAT_2_ALT = new Container(33732, "Boat 2", "trackBoatInventory"); // Alternate ID
	public static final Container BOAT_3 = new Container(965, "Boat 3", "trackBoatInventory");
	public static final Container BOAT_3_ALT = new Container(33733, "Boat 3", "trackBoatInventory"); // Alternate ID
	public static final Container BOAT_4 = new Container(966, "Boat 4", "trackBoatInventory");
	public static final Container BOAT_4_ALT = new Container(33734, "Boat 4", "trackBoatInventory"); // Alternate ID
	public static final Container BOAT_5 = new Container(967, "Boat 5", "trackBoatInventory");
	public static final Container BOAT_5_ALT = new Container(33735, "Boat 5", "trackBoatInventory"); // Alternate ID

	// Map for quick lookup by ID
	private static final Map<Integer, Container> CONTAINER_BY_ID = new HashMap<>();
	private static final Map<String, Container> CONTAINER_BY_CONFIG_KEY = new HashMap<>();

	static
	{
		// Register all containers
		registerContainer(BANK);
		registerContainer(INVENTORY);
		registerContainer(SEED_VAULT);
		registerContainer(GRAVESTONE);
		registerContainer(GROUP_STORAGE);
		registerContainer(LOOTING_BAG);
		registerContainer(POTION_STORAGE);
		registerContainer(BOAT_1);
		registerContainer(BOAT_1_ALT);
		registerContainer(BOAT_2);
		registerContainer(BOAT_2_ALT);
		registerContainer(BOAT_3);
		registerContainer(BOAT_3_ALT);
		registerContainer(BOAT_4);
		registerContainer(BOAT_4_ALT);
		registerContainer(BOAT_5);
		registerContainer(BOAT_5_ALT);
	}

	private static void registerContainer(Container container)
	{
		CONTAINER_BY_ID.put(container.getId(), container);
		CONTAINER_BY_CONFIG_KEY.put(container.getConfigKey(), container);
	}

	/**
	 * Get container by ID.
	 *
	 * @param containerId The container ID
	 * @return The Container object, or null if not found
	 */
	public static Container getContainer(int containerId)
	{
		return CONTAINER_BY_ID.get(containerId);
	}

	/**
	 * Get container by config key.
	 *
	 * @param configKey The config key (e.g., "trackBank")
	 * @return The Container object, or null if not found
	 */
	public static Container getContainerByConfigKey(String configKey)
	{
		return CONTAINER_BY_CONFIG_KEY.get(configKey);
	}

	/**
	 * Get all registered containers.
	 *
	 * @return Map of container ID to Container object
	 */
	public static Map<Integer, Container> getAllContainers()
	{
		return new HashMap<>(CONTAINER_BY_ID);
	}

	/**
	 * Gets the friendly name for a container type.
	 *
	 * @param containerId The ID of the container
	 * @return The friendly name of the container, or "Unknown" if not recognized
	 */
	public static String getContainerFriendlyName(int containerId)
	{
		Container container = CONTAINER_BY_ID.get(containerId);
		return container != null ? container.getName() : "Unknown";
	}

	/**
	 * Check if a container ID is registered.
	 *
	 * @param containerId The container ID to check
	 * @return true if the container is registered
	 */
	public static boolean isRegistered(int containerId)
	{
		return CONTAINER_BY_ID.containsKey(containerId);
	}

	/**
	 * Builds an HTML tooltip showing the breakdown of item quantities by container.
	 *
	 * @param item The tracked item to build a tooltip for
	 * @return HTML-formatted tooltip string, or null if no containers have items
	 */
	public static String buildContainerTooltip(TrackedItem item)
	{
		Map<String, Integer> containers = item.getContainerQuantities();
		if (containers == null || containers.isEmpty())
		{
			return null;
		}

		StringBuilder tooltip = new StringBuilder("<html>");
		containers.entrySet().stream()
				.filter(e -> e.getValue() > 0)
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> tooltip.append(e.getKey())
						.append(": ")
						.append(String.format("%,d", e.getValue()))
						.append("<br>"));
		tooltip.append("</html>");

		return tooltip.toString();
	}
}

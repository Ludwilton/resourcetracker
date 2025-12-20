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

	// Map for quick lookup by ID
	private static final Map<Integer, Container> CONTAINER_BY_ID = new HashMap<>();
	private static final Map<String, Container> CONTAINER_BY_CONFIG_KEY = new HashMap<>();

	static
	{
		// Register all containers
		registerContainer(BANK);
		registerContainer(INVENTORY);
		registerContainer(SEED_VAULT);
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

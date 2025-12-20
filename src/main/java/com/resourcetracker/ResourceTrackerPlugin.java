package com.resourcetracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.ChatMessageType;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = "Resource Tracker",
        description = "Track items in your bank with goals",
        tags = {"bank", "items", "tracker", "goals"}
)
public class ResourceTrackerPlugin extends Plugin
{
        @Inject
        private ClientToolbar clientToolbar;

        @Inject
        private ItemManager itemManager;

        @Inject
        private ConfigManager configManager;

        @Inject
        private ResourceTrackerConfig config;

        @Inject
        private Client client;

        @Inject
        @Getter
        private Gson gson;

        @Inject
        @Getter
        private ChatboxPanelManager chatboxPanelManager;

        @Inject
        private ChatMessageManager chatMessageManager;

        @Inject
        private net.runelite.client.ui.ClientUI clientUi;

	private ResourceTrackerPanel panel;
	private NavigationButton navButton;
	private final Map<Integer, TrackedItem> trackedItems = new HashMap<>();

	// Dynamic container cache - one map per container ID
	private final Map<Integer, Map<Integer, Integer>> containerCaches = new HashMap<>();

	@Override
	protected void startUp()
        {
                log.debug("Resource Tracker started");

                panel = new ResourceTrackerPanel(this, itemManager, chatboxPanelManager);

                final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/resourcetracker/icon.png");

                navButton = NavigationButton.builder()
                        .tooltip("Resource Tracker")
                        .icon(icon)
                        .priority(5)
                        .panel(panel)
                        .build();

                clientToolbar.addNavigation(navButton);

                loadData();
        }

        @Override
        protected void shutDown()
        {
                log.debug("Resource Tracker stopped!");
                saveData();
                clientToolbar.removeNavigation(navButton);
        }

	@SuppressWarnings("unused")
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Load tracked items when player logs in
			loadData();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Save data when logging out
			saveData();

			// Clear panel UI when logging out
			panel.resetPanel();
		}
	}

        public void sendChatMessage(String message)
        {
                chatMessageManager.queue(QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(message)
                        .build());
        }

	@SuppressWarnings("unused")
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		ContainerTracker.Container container = ContainerTracker.getContainer(containerId);

		// Check if this is a registered container and if tracking is enabled for it
		if (container != null && isContainerTrackingEnabled(container))
		{
			updateContainerCache(containerId, event.getItemContainer());
			updateTrackedItems();
		}
	}

	/**
	 * Check if tracking is enabled for a specific container via config.
	 */
	private boolean isContainerTrackingEnabled(ContainerTracker.Container container)
	{
		String configKey = container.getConfigKey();

		// Use reflection or direct mapping to check config
		switch (configKey)
		{
			case "trackBank":
				return config.trackBank();
			case "trackInventory":
				return config.trackInventory();
			case "trackSeedVault":
				return config.trackSeedVault();
			default:
				return false;
		}
	}

	/**
	 * Update the cache for a specific container.
	 * This method is generic and works for any container type.
	 */
	private void updateContainerCache(int containerId, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}

		// Get or create cache for this container
		Map<Integer, Integer> cache = containerCaches.computeIfAbsent(containerId, k -> new HashMap<>());
		cache.clear();

		// Cache all items in the container
		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				cache.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	private void updateTrackedItems()
	{
		// If no items are tracked, skip the update
		if (trackedItems.isEmpty())
		{
			return;
		}

		boolean needsUpdate = false;

		// Only iterate through tracked items, not all container items
		for (TrackedItem trackedItem : trackedItems.values())
		{
			int itemId = trackedItem.getItemId();
			int totalAmount = 0;
			Map<String, Integer> breakdown = new HashMap<>(trackedItem.getContainerQuantities());
			boolean hasScannedData = false;

			// Dynamically check all registered containers
			for (ContainerTracker.Container container : ContainerTracker.getAllContainers().values())
			{
				// Check if tracking is enabled for this container
				if (!isContainerTrackingEnabled(container))
				{
					continue;
				}

				Map<Integer, Integer> cache = containerCaches.get(container.getId());

				// If we have scanned this container
				if (cache != null && !cache.isEmpty())
				{
					Integer quantity = cache.get(itemId);
					breakdown.put(container.getName(), quantity != null ? quantity : 0);
					totalAmount += (quantity != null ? quantity : 0);
					hasScannedData = true;
				}
				// If we haven't scanned but have saved data, keep it
				else if (breakdown.containsKey(container.getName()))
				{
					totalAmount += breakdown.get(container.getName());
				}
			}

			// Only update if we have scanned data and something changed
			if (hasScannedData && (trackedItem.getCurrentAmount() != totalAmount || !trackedItem.getContainerQuantities().equals(breakdown)))
			{
				trackedItem.setCurrentAmount(totalAmount);
				trackedItem.setContainerQuantities(breakdown);
				needsUpdate = true;
			}
		}

		if (needsUpdate)
		{
			// Defer save and UI update to avoid blocking the game thread
			SwingUtilities.invokeLater(() -> {
				saveData();
				panel.rebuild();
			});
		}
	}

	public void addTrackedItem(TrackedItem item)
	{
		if (trackedItems.containsKey(item.getItemId()))
		{
			return;
		}

		trackedItems.put(item.getItemId(), item);

		// Use cached container data to populate initial quantities
		Map<String, Integer> containerQuantities = new HashMap<>();

		// Dynamically check all registered containers
		for (ContainerTracker.Container container : ContainerTracker.getAllContainers().values())
		{
			// Check if tracking is enabled for this container
			if (!isContainerTrackingEnabled(container))
			{
				continue;
			}

			Map<Integer, Integer> cache = containerCaches.get(container.getId());
			if (cache != null && cache.containsKey(item.getItemId()))
			{
				containerQuantities.put(container.getName(), cache.get(item.getItemId()));
			}
		}

		// Set the container quantities and calculate total
		if (!containerQuantities.isEmpty())
		{
			item.setContainerQuantities(containerQuantities);
			int total = containerQuantities.values().stream().mapToInt(Integer::intValue).sum();
			item.setCurrentAmount(total);
		}

		SwingUtilities.invokeLater(() -> panel.rebuild());
		saveData();
	}

	public void removeTrackedItem(int itemId)
	{
		trackedItems.remove(itemId);
		SwingUtilities.invokeLater(() -> panel.rebuild());
		saveData();
	}

	public Map<Integer, TrackedItem> getTrackedItems()
	{
		return trackedItems;
	}


	public void saveData()
	{
		if (trackedItems.isEmpty())
		{
			configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", "");
			return;
		}

		Gson gson = new Gson();
		List<TrackedItem> itemList = new ArrayList<>(trackedItems.values());
		String json = gson.toJson(itemList);
		log.debug("Saving {} tracked items to config", trackedItems.size());
		configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", json);
	}

	private void loadData()
	{
		String json = configManager.getRSProfileConfiguration("resourcetracker", "trackedItems");
		if (json == null || json.isEmpty())
		{
			log.debug("No tracked items to load from config");
			return;
		}

		try
		{
			Gson gson = new Gson();
			Type type = new TypeToken<List<TrackedItem>>()
			{
			}.getType();
			List<TrackedItem> itemList = gson.fromJson(json, type);

			if (itemList == null)
			{
				log.warn("Failed to deserialize tracked items - null result");
				return;
			}

			trackedItems.clear();
			for (TrackedItem item : itemList)
			{
				if (item != null && item.getItemId() > 0)
				{
					// Ensure containerQuantities is not null
					if (item.getContainerQuantities() == null)
					{
						item.setContainerQuantities(new HashMap<>());
					}
					// Ensure category is not null
					if (item.getCategory() == null || item.getCategory().isEmpty())
					{
						item.setCategory("Default");
					}
					trackedItems.put(item.getItemId(), item);
				}
			}

			log.debug("Loaded {} tracked items from config", trackedItems.size());
			SwingUtilities.invokeLater(() -> panel.loadItems(trackedItems.values()));
		}
		catch (Exception e)
		{
			log.error("Error loading tracked items from config", e);
		}
	}

	public net.runelite.client.ui.ClientUI getClientUi() {
		return clientUi;
	}

	@Provides
	ResourceTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ResourceTrackerConfig.class);
	}
}

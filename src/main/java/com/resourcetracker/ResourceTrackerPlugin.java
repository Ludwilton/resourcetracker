package com.resourcetracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
	private ClientThread clientThread;

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

	// Track category order for consistent display
	private final List<String> categoryOrder = new ArrayList<>();

	// Dynamic container cache - one map per container ID
	private final Map<Integer, Map<Integer, Integer>> containerCaches = new HashMap<>();

	// Potion storage tracking
	private boolean rebuildPotions = false;
	private Set<Integer> potionStoreVars;

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

			// Check if bank is already open and potion storage should be initialized
			// Must be called on client thread
			if (config.trackPotionStorage())
			{
				clientThread.invokeLater(() ->
				{
					if (client.getItemContainer(InventoryID.BANK) != null)
					{
						rebuildPotions = true;
					}
				});
			}
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Save data when logging out
			saveData();
			// Clear panel when logging out
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

	public void resetAllData()
	{
		// Clear in-memory data
		trackedItems.clear();
		categoryOrder.clear();
		containerCaches.clear();

		// Clear from config
		configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", "");
		configManager.setRSProfileConfiguration("resourcetracker", "categoryOrder", "");

		// Reset the panel UI
		SwingUtilities.invokeLater(() -> {
			panel.resetPanel();
			sendChatMessage("All tracked items and categories have been reset.");
		});

		log.info("Reset all tracked items and categories");
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
			case "trackRetrievalService":
				return config.trackRetrievalService();
			case "trackGroupStorage":
				return config.trackGroupStorage();
			case "trackLootingBag":
				return config.trackLootingBag();
			case "trackPotionStorage":
				return config.trackPotionStorage();
			case "trackBoatInventory":
				return config.trackBoatInventory();
            case "trackPOHStorage":
                return config.trackPOHStorage();
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

		// Normalize container IDs to use the same cache for alternate IDs
		int cacheId = normalizeContainerId(containerId);

		// Get or create cache for this container
		Map<Integer, Integer> cache = containerCaches.computeIfAbsent(cacheId, k -> new HashMap<>());
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

	/**
	 * Normalize container IDs to use the same cache for alternate IDs.
	 * Examples: 660 -> 93 (temp inventory), 33731 -> 963 (boat 1 alternate)
	 */
	private int normalizeContainerId(int containerId)
	{

		// Boats: Normalize alternate IDs (33731-33735) to primary IDs (963-967)
		if (containerId >= 33731 && containerId <= 33735)
		{
			return 963 + (containerId - 33731); // 33731->963, 33732->964, etc.
		}

		return containerId; // No normalization needed
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
			Map<String, Integer> breakdown = new HashMap<>();
			Map<String, Integer> savedBreakdown = trackedItem.getContainerQuantities();
			boolean hasScannedData = false;

			// Track which normalized cache IDs we've already processed to avoid double-counting
			java.util.Set<Integer> processedCaches = new java.util.HashSet<>();

			// Dynamically check all registered containers
			for (ContainerTracker.Container container : ContainerTracker.getAllContainers().values())
			{
				// Check if tracking is enabled for this container
				if (!isContainerTrackingEnabled(container))
				{
					continue;
				}

				// Get the normalized cache ID to avoid counting alternate IDs twice
				int normalizedCacheId = normalizeContainerId(container.getId());

				// Skip if we've already processed this cache (e.g., alternate ID for same container)
				if (processedCaches.contains(normalizedCacheId))
				{
					continue;
				}

			processedCaches.add(normalizedCacheId);
			Map<Integer, Integer> cache = containerCaches.get(normalizedCacheId);

			// If we have scanned this container (cache exists, even if empty)
			if (cache != null)
			{
				Integer quantity = cache.get(itemId);
				int qty = (quantity != null ? quantity : 0);
				breakdown.put(container.getName(), qty);
				totalAmount += qty;
				hasScannedData = true;
			}
			// If we haven't scanned but have saved data, keep it
			else if (savedBreakdown != null && savedBreakdown.containsKey(container.getName()))
			{
				int savedQty = savedBreakdown.get(container.getName());
				breakdown.put(container.getName(), savedQty);
				totalAmount += savedQty;
			}
			}

			// Only update if we have scanned data and something changed
			if (hasScannedData && (trackedItem.getCurrentAmount() != totalAmount || !breakdown.equals(savedBreakdown)))
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

		// Track which normalized cache IDs we've already processed to avoid double-counting
		java.util.Set<Integer> processedCaches = new java.util.HashSet<>();

		// Dynamically check all registered containers
		for (ContainerTracker.Container container : ContainerTracker.getAllContainers().values())
		{
			// Check if tracking is enabled for this container
			if (!isContainerTrackingEnabled(container))
			{
				continue;
			}

			// Get the normalized cache ID to avoid counting alternate IDs twice
			int normalizedCacheId = normalizeContainerId(container.getId());

			// Skip if we've already processed this cache (e.g., alternate ID for same container)
			if (processedCaches.contains(normalizedCacheId))
			{
				continue;
			}

			processedCaches.add(normalizedCacheId);
			Map<Integer, Integer> cache = containerCaches.get(normalizedCacheId);
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

	/**
	 * Detect when bank finishes building to trigger potion storage rebuild.
	 */
	@SuppressWarnings("unused")
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING && config.trackPotionStorage())
		{
			rebuildPotions = true;
		}
	}

	/**
	 * On client tick, rebuild potion storage if flagged and cache the varbit triggers.
	 */
	@SuppressWarnings("unused")
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (rebuildPotions)
		{
			updatePotionStorageCache();
			rebuildPotions = false;

			// Cache the varbits that trigger potion store rebuilds (only do this once)
			Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			if (w != null && potionStoreVars == null)
			{
				int[] trigger = w.getVarTransmitTrigger();
				potionStoreVars = new HashSet<>();
				Arrays.stream(trigger).forEach(potionStoreVars::add);
			}
		}
	}

	/**
	 * Watch for varbit changes that affect potion storage.
	 */
	@SuppressWarnings("unused")
	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (potionStoreVars != null && potionStoreVars.contains(varbitChanged.getVarpId()))
		{
			rebuildPotions = true;
		}
	}

	/**
	 * Update the potion storage cache by reading from game enums and scripts.
	 * potion storage doesn't have a normal ItemContainer.
	 */
	private void updatePotionStorageCache()
	{
		final Map<Integer, Integer> potionQtyMap = new HashMap<>();

		// Get potion enums from the client
		EnumComposition potionStorePotions = client.getEnum(EnumID.POTIONSTORE_POTIONS);
		EnumComposition potionStoreUnfinishedPotions = client.getEnum(EnumID.POTIONSTORE_UNFINISHED_POTIONS);

		// Process both regular and unfinished potions
		for (EnumComposition e : new EnumComposition[]{potionStorePotions, potionStoreUnfinishedPotions})
		{
			for (int potionEnumId : e.getIntVals())
			{
				EnumComposition potionEnum = client.getEnum(potionEnumId);

				// Run the script to get the dose count for this potion
				client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnumId);
				int doses = client.getIntStack()[0];

				if (doses > 0)
				{
					for (int doseLevel = 1; doseLevel <= 4; doseLevel++)
					{
						int itemId = potionEnum.getIntValue(doseLevel);
						if (itemId > 0)
						{
							// Convert total doses into containers of this dose level
							int quantity = doses / doseLevel;
							potionQtyMap.put(itemId, quantity);
						}
					}
				}
			}
		}

		// Update the cache with potion storage fake container ID
		int potionStorageId = ContainerTracker.POTION_STORAGE.getId();
		int cacheId = normalizeContainerId(potionStorageId);
		Map<Integer, Integer> cache = containerCaches.computeIfAbsent(cacheId, k -> new HashMap<>());
		cache.clear();
		cache.putAll(potionQtyMap);

		log.debug("Updated potion storage cache with {} potion types", potionQtyMap.size());

		// Update tracked items
		updateTrackedItems();
	}


	public void saveData()
	{
		if (trackedItems.isEmpty())
		{
			configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", "");
			saveCategoryOrder(); // Still save order even if no items
			return;
		}

		Gson gson = new Gson();
		List<TrackedItem> itemList = new ArrayList<>(trackedItems.values());
		String json = gson.toJson(itemList);
		log.debug("Saving {} tracked items to config", trackedItems.size());
		configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", json);

		// Save category order
		saveCategoryOrder();
	}

	private void saveCategoryOrder()
	{
		String orderJson = gson.toJson(categoryOrder);
		configManager.setRSProfileConfiguration("resourcetracker", "categoryOrder", orderJson);
		log.debug("Saved category order: {}", categoryOrder);
	}

	private void loadCategoryOrder()
	{
		String orderJson = configManager.getRSProfileConfiguration("resourcetracker", "categoryOrder");
		if (orderJson != null && !orderJson.isEmpty())
		{
			try
			{
				Type type = new TypeToken<List<String>>(){}.getType();
				List<String> loaded = gson.fromJson(orderJson, type);
				if (loaded != null)
				{
					categoryOrder.clear();
					categoryOrder.addAll(loaded);
					log.debug("Loaded category order: {}", categoryOrder);
				}
			}
			catch (Exception e)
			{
				log.error("Error loading category order", e);
			}
		}
	}

	public void registerCategory(String categoryName)
	{
		if (!categoryOrder.contains(categoryName))
		{
			categoryOrder.add(categoryName);
			saveCategoryOrder();
			log.debug("Registered new category: {}", categoryName);
		}
	}

	public void removeCategory(String categoryName)
	{
		categoryOrder.remove(categoryName);
		saveCategoryOrder();
		log.debug("Removed category: {}", categoryName);
	}

	public void renameCategory(String oldName, String newName)
	{
		int index = categoryOrder.indexOf(oldName);
		if (index != -1)
		{
			categoryOrder.set(index, newName);
			saveCategoryOrder();
			saveData();
			log.debug("Renamed category '{}' to '{}'", oldName, newName);
		}
	}

	public void moveCategoryOrder(String categoryName, int newIndex)
	{
		categoryOrder.remove(categoryName);
		categoryOrder.add(newIndex, categoryName);
		saveCategoryOrder();
		SwingUtilities.invokeLater(() -> panel.rebuild());
		log.debug("Moved category {} to index {}", categoryName, newIndex);
	}

	public List<String> getCategoryOrder()
	{
		return new ArrayList<>(categoryOrder);
	}

	private void loadData()
	{
		// Load category order first
		loadCategoryOrder();

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

					// Fetch and set prices if not already set (for backwards compatibility)
					if (item.getGePrice() == 0)
					{
						item.setGePrice(itemManager.getItemPrice(item.getItemId()));
					}
					if (item.getHaPrice() == 0)
					{
						item.setHaPrice(itemManager.getItemComposition(item.getItemId()).getHaPrice());
					}

					trackedItems.put(item.getItemId(), item);

					// Register category if not already in order
					if (!categoryOrder.contains(item.getCategory()))
					{
						categoryOrder.add(item.getCategory());
					}
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

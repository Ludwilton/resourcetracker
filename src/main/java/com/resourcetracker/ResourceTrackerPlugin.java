package com.resourcetracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
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
	private static final int BANK_INVENTORY_ID = 95;

	@Inject
	private Client client;

	@Inject
	private ResourceTrackerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	private ResourceTrackerPanel panel;
	private NavigationButton navButton;
	private final Gson gson = new Gson();

	public Gson getGson()
	{
		return gson;
	}

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Resource Tracker started");

		panel = new ResourceTrackerPanel(this, itemManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Resource Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		// Load items immediately - RSProfile is available on startup
		loadTrackedItems();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Resource Tracker stopped!");
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Load tracked items when player logs in
			loadTrackedItems();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Clear panel when logging out
			panel.resetPanel();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == BANK_INVENTORY_ID)
		{
			updateBankItems(event.getItemContainer());
		}
	}

	private void updateBankItems(ItemContainer itemContainer)
	{
		if (itemContainer == null)
		{
			return;
		}

		Map<Integer, Integer> bankItems = new HashMap<>();
		for (Item item : itemContainer.getItems())
		{
			if (item.getId() > 0)
			{
				bankItems.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		boolean needsUpdate = false;
		for (TrackedItem trackedItem : panel.getTrackedItems())
		{
			int amount = bankItems.getOrDefault(trackedItem.getItemId(), 0);
			if (panel.updateItemAmount(trackedItem.getItemId(), amount))
			{
				needsUpdate = true;
			}
		}

		if (needsUpdate)
		{
			saveTrackedItems(panel.getTrackedItems());
			panel.rebuild();
		}
	}

	public void saveTrackedItems(List<TrackedItem> items)
	{
		String json = gson.toJson(items);
		log.debug("Saving tracked items: {}", json);
		configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", json);
	}

	public List<TrackedItem> getTrackedItems()
	{
		return panel.getTrackedItems();
	}

	public void removeTrackedItem(TrackedItem item)
	{
		panel.removeTrackedItem(item);
	}

	private void loadTrackedItems()
	{
		String json = configManager.getRSProfileConfiguration("resourcetracker", "trackedItems");
		log.debug("Loading tracked items: {}", json);

		if (json == null || json.isEmpty())
		{
			log.debug("No tracked items found in profile");
			return;
		}

		try
		{
			Type listType = new TypeToken<ArrayList<TrackedItem>>(){}.getType();
			List<TrackedItem> items = gson.fromJson(json, listType);
			if (items != null)
			{
				log.debug("Loaded {} items", items.size());
				panel.setTrackedItems(items);
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load tracked items", e);
		}
	}

	@Provides
	ResourceTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ResourceTrackerConfig.class);
	}
}

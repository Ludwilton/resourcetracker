package com.resourcetracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.gameval.InventoryID;

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
	private EventBus eventBus;

	@Inject
	@Getter
	private Gson gson;

	private ResourceTrackerPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		log.debug("Resource Tracker started");

		panel = new ResourceTrackerPanel(this, itemManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/resourcetracker/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Resource Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		eventBus.register(this);

		loadTrackedItems();
	}

	@Override
	protected void shutDown()
	{
		log.debug("Resource Tracker stopped!");
		eventBus.unregister(this);
		saveTrackedItems(panel.getTrackedItems());
		clientToolbar.removeNavigation(navButton);
	}

	@SuppressWarnings("unused")
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

	@SuppressWarnings("unused")
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
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
		List<TrackedItem> items = panel.getTrackedItems();

		for (TrackedItem trackedItem : items)
		{
			int amount = bankItems.getOrDefault(trackedItem.getItemId(), 0);
			if (trackedItem.getCurrentAmount() != amount)
			{
				trackedItem.setCurrentAmount(amount);
				needsUpdate = true;
			}
		}

		if (needsUpdate)
		{
			saveTrackedItems(items);
			SwingUtilities.invokeLater(() -> panel.rebuild());
		}
	}

	public void saveTrackedItems(List<TrackedItem> items)
	{
		String json = gson.toJson(items);
		log.debug("Saving tracked items: {}", json);
		configManager.setRSProfileConfiguration("resourcetracker", "trackedItems", json);
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

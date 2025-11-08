package com.resourcetracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ResourceTrackerPanel extends PluginPanel implements Scrollable
{
	private final ResourceTrackerPlugin plugin;
	private final ItemManager itemManager;

	private final JPanel itemListPanel;
	private final JPanel searchResultsPanel;
	private final JPanel contentWrapper;
	private final JScrollPane searchScrollPane;
	private final JScrollPane itemScrollPane;
	private final IconTextField searchBar;
	private final List<TrackedItem> trackedItems;
	private final List<CategoryBox> categoryBoxes = new ArrayList<>();
	private String selectedCategory = null;
	private static final int MAX_SEARCH_RESULTS = 30;
	private Timer searchDebounceTimer;

	public ResourceTrackerPanel(ResourceTrackerPlugin plugin, ItemManager itemManager)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.trackedItems = new ArrayList<>();

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		// Create main layout panel
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		// Search area - always visible at top
		JPanel searchAreaPanel = new JPanel();
		searchAreaPanel.setLayout(new BoxLayout(searchAreaPanel, BoxLayout.Y_AXIS));
		searchAreaPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchAreaPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR.darker()),
			new EmptyBorder(5, 5, 5, 5)
		));

		// Category name input field
		JTextField categoryNameField = new JTextField();
		categoryNameField.setPreferredSize(new Dimension(0, 30));
		categoryNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		categoryNameField.setForeground(Color.GRAY);
		categoryNameField.setText("New category name...");
		categoryNameField.setCaretColor(Color.WHITE);
		categoryNameField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusGained(java.awt.event.FocusEvent e)
			{
				if (categoryNameField.getText().equals("New category name..."))
				{
					categoryNameField.setText("");
					categoryNameField.setForeground(Color.WHITE);
				}
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				if (categoryNameField.getText().isEmpty())
				{
					categoryNameField.setText("New category name...");
					categoryNameField.setForeground(Color.GRAY);
				}
			}
		});
		categoryNameField.addActionListener(e ->
		{
			String categoryName = categoryNameField.getText().trim();
			if (!categoryName.isEmpty() && !categoryName.equals("New category name..."))
			{
				createNewCategory(categoryName);
				categoryNameField.setText("New category name...");
				categoryNameField.setForeground(Color.GRAY);
			}
		});

		searchAreaPanel.add(categoryNameField);

		// Search bar
		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setEnabled(false); // Disabled by default
		searchBar.setToolTipText("Open a category to search for items");
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}
		});
		searchBar.addClearListener(this::onSearchChanged);

		searchAreaPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		searchAreaPanel.add(searchBar);


		layoutPanel.add(searchAreaPanel);

		// Wrapper panel for content (search results OR tracked items)
		contentWrapper = new JPanel(new BorderLayout());
		contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		layoutPanel.add(contentWrapper);

		// Search results panel - shown when searching
		searchResultsPanel = new JPanel();
		searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
		searchResultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchScrollPane = new JScrollPane(searchResultsPanel);
		searchScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		searchScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		searchScrollPane.setBorder(null);

		// Tracked items panel
		itemListPanel = new JPanel();
		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));
		itemListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		itemScrollPane = new JScrollPane(itemListPanel);
		itemScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		itemScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		itemScrollPane.setBorder(null);

		// Start with items panel visible
		contentWrapper.add(itemScrollPane, BorderLayout.CENTER);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}

	public void addTrackedItem(TrackedItem item)
	{
		trackedItems.add(item);
		plugin.saveTrackedItems(trackedItems);
		rebuildTrackedItems();
	}

	public void removeTrackedItem(TrackedItem item)
	{
		trackedItems.remove(item);
		plugin.saveTrackedItems(trackedItems);
		rebuildTrackedItems();
	}

	public void updateItemAmount(int itemId, int amount)
	{
		boolean updated = false;
		for (TrackedItem item : trackedItems)
		{
			if (item.getItemId() == itemId)
			{
				item.setCurrentAmount(amount);
				updated = true;
			}
		}
		if (updated)
		{
			plugin.saveTrackedItems(trackedItems);
			rebuildTrackedItems();
		}
	}

	public List<TrackedItem> getTrackedItems()
	{
		return trackedItems;
	}

	public void setTrackedItems(List<TrackedItem> items)
	{
		trackedItems.clear();
		trackedItems.addAll(items);
		rebuildTrackedItems();
	}

	public void rebuild()
	{
		rebuildTrackedItems();
	}

	public void setSelectedCategory(String category)
	{
		// Close all other categories, open this one
		selectedCategory = category;
		searchBar.setEnabled(true); // Enable search when category is selected

		// Update search bar placeholder/tooltip
		searchBar.setToolTipText("Search items to add to " + category);

		for (CategoryBox box : categoryBoxes)
		{
			if (!box.getCategoryName().equals(category) && !box.isCollapsed())
			{
				box.collapse();
			}
		}
	}

	public void deleteCategory(String categoryName)
	{
		// Remove all items in this category
		trackedItems.removeIf(item -> item.getCategory().equals(categoryName));

		// Clear selected category if it's the one being deleted
		if (categoryName.equals(selectedCategory))
		{
			selectedCategory = null;
			searchBar.setEnabled(false);
			searchBar.setToolTipText("Open a category to search for items");
		}

		plugin.saveTrackedItems(trackedItems);
		rebuildTrackedItems();
	}

	private void createNewCategory(String categoryName)
	{
		if (categoryName == null || categoryName.trim().isEmpty())
		{
			return;
		}

		String trimmedCategoryName = categoryName.trim();

		// Check if category already exists
		boolean exists = trackedItems.stream()
			.anyMatch(item -> item.getCategory().equalsIgnoreCase(trimmedCategoryName));

		if (exists)
		{
			return; // Silently ignore duplicates
		}

		// Set as selected and focus search
		selectedCategory = trimmedCategoryName;
		searchBar.setEnabled(true);
		searchBar.requestFocusInWindow();

		// Force rebuild to show the empty category
		rebuildTrackedItems();
	}

	private void scheduleSearch()
	{
		// Cancel any pending search
		if (searchDebounceTimer != null)
		{
			searchDebounceTimer.stop();
		}

		// Schedule new search with 200ms delay
		searchDebounceTimer = new Timer(200, e -> {
			onSearchChanged();
			searchDebounceTimer = null;
		});
		searchDebounceTimer.setRepeats(false);
		searchDebounceTimer.start();
	}

	private void onSearchChanged()
	{
		String query = searchBar.getText().trim();

		if (query.isEmpty())
		{
			// Show tracked items
			contentWrapper.removeAll();
			contentWrapper.add(itemScrollPane, BorderLayout.CENTER);
			contentWrapper.revalidate();
			contentWrapper.repaint();
		}
		else
		{
			// Show search results
			contentWrapper.removeAll();
			contentWrapper.add(searchScrollPane, BorderLayout.CENTER);
			showSearchResults(query);
			contentWrapper.revalidate();
			contentWrapper.repaint();
		}
	}

	private void showSearchResults(String query)
	{
		SwingUtil.fastRemoveAll(searchResultsPanel);

		String lowerQuery = query.toLowerCase();

		// Use ItemManager to search for items
		List<ItemDefinition> results = itemManager.search(query).stream()
			.filter(itemPrice -> {
				String itemName = itemPrice.getName().toLowerCase();
				// Filter out placeholder, noted, and other variants
				return !itemName.contains("->") && !itemName.equals("null");
			})
			.map(itemPrice -> new ItemDefinition(itemPrice.getId(), itemPrice.getName()))
			.sorted(Comparator.comparingInt((ItemDefinition item) -> {
				String itemName = item.getName().toLowerCase();
				if (itemName.equals(lowerQuery)) return 0;
				if (itemName.startsWith(lowerQuery)) return 1;
				return 2;
			}).thenComparing(item -> item.getName().length()))
			.limit(MAX_SEARCH_RESULTS)
			.collect(Collectors.toList());

		for (ItemDefinition item : results)
		{
			JPanel itemPanel = createSearchResultPanel(item);
			searchResultsPanel.add(itemPanel);
			searchResultsPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		}

		if (results.isEmpty())
		{
			JLabel noResults = new JLabel("No items found");
			noResults.setForeground(Color.LIGHT_GRAY);
			noResults.setBorder(new EmptyBorder(10, 10, 10, 10));
			searchResultsPanel.add(noResults);
		}

		// Add glue to push items to the top
		searchResultsPanel.add(Box.createVerticalGlue());

		searchResultsPanel.revalidate();
		searchResultsPanel.repaint();
	}

	private JPanel createSearchResultPanel(ItemDefinition itemDef)
	{
		JPanel panel = new JPanel(new BorderLayout(5, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(8, 10, 8, 10));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 50));

		// Left: Icon and Name
		JPanel leftPanel = new JPanel(new BorderLayout(5, 0));
		leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(36, 32));
		AsyncBufferedImage itemImage = itemManager.getImage(itemDef.getId());
		if (itemImage != null)
		{
			itemImage.addTo(iconLabel);
		}

		JLabel nameLabel = new JLabel(itemDef.getName());
		nameLabel.setForeground(Color.WHITE);

		leftPanel.add(iconLabel, BorderLayout.WEST);
		leftPanel.add(nameLabel, BorderLayout.CENTER);

		panel.add(leftPanel, BorderLayout.CENTER);

		// Right: Goal input and + button
		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		rightPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JTextField goalField = new JTextField(5);
		goalField.setPreferredSize(new Dimension(60, 25));
		goalField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		goalField.setForeground(Color.WHITE);
		goalField.setCaretColor(Color.WHITE);
		goalField.setToolTipText("Enter goal amount");

		JButton addButton = new JButton("+");
		addButton.setPreferredSize(new Dimension(35, 25));
		addButton.setMinimumSize(new Dimension(35, 25));
		addButton.setMaximumSize(new Dimension(35, 25));
		addButton.setToolTipText("Add to tracking");
		addButton.addActionListener(e -> {
			String goalText = goalField.getText().trim();
			if (goalText.isEmpty())
			{
				return; // Ignore empty input
			}

			try
			{
				int goal = Integer.parseInt(goalText);
				if (goal <= 0)
				{
					return; // Ignore invalid input
				}

				// Check if already tracked
				for (TrackedItem existing : trackedItems)
				{
					if (existing.getItemId() == itemDef.getId())
					{
						return; // Ignore duplicates
					}
				}

				// Use selected category
				if (selectedCategory == null || selectedCategory.isEmpty())
				{
					return; // Should not happen since search is disabled without category
				}

				TrackedItem newItem = new TrackedItem(itemDef.getId(), itemDef.getName(), goal, selectedCategory);

				// Add the item
				trackedItems.add(newItem);
				plugin.saveTrackedItems(trackedItems);

				// Clear the goal field immediately
				goalField.setText("");

				// Hide search and show updated categories
				searchBar.setText("");
				contentWrapper.removeAll();
				contentWrapper.add(itemScrollPane, BorderLayout.CENTER);
				contentWrapper.revalidate();
				contentWrapper.repaint();

				// Force rebuild
				rebuildTrackedItems();
			}
			catch (NumberFormatException ex)
			{
				// Ignore invalid input
			}
		});

		rightPanel.add(goalField);
		rightPanel.add(addButton);

		panel.add(rightPanel, BorderLayout.EAST);

		return panel;
	}


	private void rebuildTrackedItems()
	{
		SwingUtilities.invokeLater(() ->
		{
			SwingUtil.fastRemoveAll(itemListPanel);
			categoryBoxes.clear();

			// Ensure all items have a category (for backward compatibility)
			trackedItems.forEach(item -> {
				if (item.getCategory() == null || item.getCategory().isEmpty())
				{
					item.setCategory("Default");
				}
			});

			// Group items by category
			java.util.Map<String, List<TrackedItem>> itemsByCategory = trackedItems.stream()
				.collect(Collectors.groupingBy(TrackedItem::getCategory));

			// If we have a selected category but it's not in the map, add it as empty
			if (selectedCategory != null && !itemsByCategory.containsKey(selectedCategory))
			{
				itemsByCategory.put(selectedCategory, new ArrayList<>());
			}

			// Create all CategoryBoxes first (like LootTracker buildBox pattern)
			for (java.util.Map.Entry<String, List<TrackedItem>> entry : itemsByCategory.entrySet())
			{
				CategoryBox categoryBox = new CategoryBox(entry.getKey(), plugin, itemManager, this);
				categoryBoxes.add(categoryBox);

				// Build the box with its items
				categoryBox.rebuild(entry.getValue());

				// Add to panel
				itemListPanel.add(categoryBox);
			}

			// Now expand/collapse AFTER all boxes are added (avoids revalidation during build)
			for (CategoryBox box : categoryBoxes)
			{
				if (box.getCategoryName().equals(selectedCategory))
				{
					box.expand();
				}
				else
				{
					box.collapse();
				}
			}

			// Single revalidate at the very end
			itemListPanel.revalidate();
		});
	}


	private static class ItemDefinition
	{
		private final int id;
		private final String name;

		ItemDefinition(int id, String name)
		{
			this.id = id;
			this.name = name;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}
	}
}

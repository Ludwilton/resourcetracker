package com.resourcetracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ResourceTrackerPanel extends PluginPanel
{
	private final ResourceTrackerPlugin plugin;
	private final ItemManager itemManager;

	// When there is no tracked items, display this
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();

	private final JPanel itemListPanel;
	private final JPanel topPanel;
	private final IconTextField searchBar;
	private final List<TrackedItem> trackedItems;
	private final List<ItemDefinition> availableItems;
	private boolean searchMode = false;
	private String searchTargetCategory = null; // Category to add searched items to
	private static final int MAX_SEARCH_RESULTS = 30;
	private Timer searchDebounceTimer;

	public ResourceTrackerPanel(ResourceTrackerPlugin plugin, ItemManager itemManager)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.trackedItems = new ArrayList<>();
		this.availableItems = new ArrayList<>();

		initializeItemList();

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		// Create layout panel for wrapping
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		// Top panel with category menu button and search bar
		topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Category menu button
		JPanel categoryMenuPanel = new JPanel(new BorderLayout());
		categoryMenuPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		categoryMenuPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

		JButton addCategoryButton = new JButton("+ New Category");
		addCategoryButton.setPreferredSize(new Dimension(0, 30));
		addCategoryButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addCategoryButton.setForeground(Color.WHITE);
		addCategoryButton.setFocusPainted(false);
		addCategoryButton.addActionListener(e -> createNewCategory());
		categoryMenuPanel.add(addCategoryButton, BorderLayout.CENTER);

		topPanel.add(categoryMenuPanel);

		// Search bar
		JPanel searchPanel = new JPanel(new BorderLayout());
		searchPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		searchPanel.setBorder(new EmptyBorder(0, 5, 5, 5));

		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(100, 30));
		searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setVisible(false); // Hidden by default
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

		searchPanel.add(searchBar, BorderLayout.CENTER);
		topPanel.add(searchPanel);


		layoutPanel.add(topPanel);

		// Scrollable item list - wrapped in scroll pane
		itemListPanel = new JPanel();
		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));
		itemListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(itemListPanel);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		layoutPanel.add(scrollPane);

		// Add error panel
		errorPanel.setContent("Resource Tracker", "You have not tracked any items yet.");
		add(errorPanel);
	}

	public void addTrackedItem(TrackedItem item)
	{
		trackedItems.add(item);
		plugin.saveTrackedItems(trackedItems);
		rebuild();
	}

	public void removeTrackedItem(TrackedItem item)
	{
		trackedItems.remove(item);
		plugin.saveTrackedItems(trackedItems);
		rebuild();
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
			rebuild();
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
		rebuild();
	}

	private void initializeItemList()
	{
		// Load all items from ItemManager's cache
		// Note: ItemManager.search() will be used dynamically during search
		// We don't need to pre-populate availableItems anymore
	}

	private void createNewCategory()
	{
		String categoryName = JOptionPane.showInputDialog(this, "Enter category name:", "New Category", JOptionPane.PLAIN_MESSAGE);
		if (categoryName != null && !categoryName.trim().isEmpty())
		{
			final String trimmedCategoryName = categoryName.trim();

			// Check if category already exists
			boolean exists = trackedItems.stream()
				.anyMatch(item -> item.getCategory().equalsIgnoreCase(trimmedCategoryName));

			if (exists)
			{
				JOptionPane.showMessageDialog(this, "Category already exists!", "Duplicate Category", JOptionPane.WARNING_MESSAGE);
				return;
			}

			// Show search for this category
			showSearchForCategory(trimmedCategoryName);
		}
	}

	public void showSearchForCategory(String category)
	{
		searchTargetCategory = category;
		searchMode = true;
		searchBar.setText("");
		searchBar.setVisible(true);
		searchBar.requestFocusInWindow();
		rebuild();
	}

	private void closeSearch()
	{
		searchMode = false;
		searchTargetCategory = null;
		searchBar.setText("");
		searchBar.setVisible(false);
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

		if (query.isEmpty() && searchMode)
		{
			// If search was cleared, stay in search mode but show empty state
			SwingUtil.fastRemoveAll(itemListPanel);
			itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));

			JLabel hint = new JLabel("<html><center>Search for items to add to<br>" + (searchTargetCategory != null ? searchTargetCategory : "category") + "</center></html>");
			hint.setForeground(Color.LIGHT_GRAY);
			hint.setBorder(new EmptyBorder(20, 10, 20, 10));
			hint.setAlignmentX(Component.CENTER_ALIGNMENT);
			itemListPanel.add(hint);

			JButton cancelButton = new JButton("Cancel");
			cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
			cancelButton.addActionListener(e -> closeSearch());
			itemListPanel.add(cancelButton);

			itemListPanel.add(Box.createVerticalGlue());
			itemListPanel.revalidate();
			itemListPanel.repaint();
		}
		else if (!query.isEmpty())
		{
			showSearchResults(query);
		}
	}

	private void showSearchResults(String query)
	{
		SwingUtil.fastRemoveAll(itemListPanel);

		// Use vertical box layout for search results
		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));

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
			itemListPanel.add(itemPanel);
			itemListPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		}

		if (results.isEmpty())
		{
			JLabel noResults = new JLabel("No items found");
			noResults.setForeground(Color.LIGHT_GRAY);
			noResults.setBorder(new EmptyBorder(10, 10, 10, 10));
			itemListPanel.add(noResults);
		}

		// Add glue to push items to the top
		itemListPanel.add(Box.createVerticalGlue());

		itemListPanel.revalidate();
		itemListPanel.repaint();
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
				JOptionPane.showMessageDialog(panel,
					"Please enter a goal amount.",
					"Missing Goal",
					JOptionPane.WARNING_MESSAGE);
				return;
			}

			try
			{
				int goal = Integer.parseInt(goalText);
				if (goal <= 0)
				{
					JOptionPane.showMessageDialog(panel,
						"Goal must be a positive number.",
						"Invalid Goal",
						JOptionPane.ERROR_MESSAGE);
					return;
				}

				// Check if already tracked
				for (TrackedItem existing : trackedItems)
				{
					if (existing.getItemId() == itemDef.getId())
					{
						JOptionPane.showMessageDialog(panel,
							"This item is already being tracked!",
							"Duplicate Item",
							JOptionPane.WARNING_MESSAGE);
						return;
					}
				}

				// Use target category if set (from + button), otherwise ask
				String category;
				if (searchTargetCategory != null)
				{
					category = searchTargetCategory;
				}
				else
				{
					category = "Default";
				}

				TrackedItem newItem = new TrackedItem(itemDef.getId(), itemDef.getName(), goal, category);
				addTrackedItem(newItem);

				// Stay in search mode to add more items
				goalField.setText("");
				goalField.requestFocusInWindow();
			}
			catch (NumberFormatException ex)
			{
				JOptionPane.showMessageDialog(panel,
					"Please enter a valid number.",
					"Invalid Input",
					JOptionPane.ERROR_MESSAGE);
			}
		});

		rightPanel.add(goalField);
		rightPanel.add(addButton);

		panel.add(rightPanel, BorderLayout.EAST);

		return panel;
	}


	private void rebuild()
	{
		if (searchMode)
		{
			onSearchChanged();
		}
		else
		{
			rebuildTrackedItems();
		}
	}

	private void rebuildTrackedItems()
	{
		SwingUtil.fastRemoveAll(itemListPanel);

		if (trackedItems.isEmpty())
		{
			// Show error panel
			errorPanel.setContent("Resource Tracker", "You have not tracked any items yet.<br>Click '+ New Category' to get started.");
			if (errorPanel.getParent() == null)
			{
				add(errorPanel);
			}
			revalidate();
			repaint();
			return;
		}

		// Hide error panel when we have items
		remove(errorPanel);

		// Use vertical box layout for category boxes
		itemListPanel.setLayout(new BoxLayout(itemListPanel, BoxLayout.Y_AXIS));

		// Group items by category
		java.util.Map<String, List<TrackedItem>> itemsByCategory = trackedItems.stream()
			.collect(Collectors.groupingBy(TrackedItem::getCategory));

		// Create a CategoryBox for each category
		for (java.util.Map.Entry<String, List<TrackedItem>> entry : itemsByCategory.entrySet())
		{
			CategoryBox categoryBox = new CategoryBox(entry.getKey(), plugin, itemManager, this);
			categoryBox.updateItems(entry.getValue());
			categoryBox.setAlignmentX(Component.LEFT_ALIGNMENT);
			itemListPanel.add(categoryBox);
			itemListPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		}

		// Add glue to push everything to top
		itemListPanel.add(Box.createVerticalGlue());

		itemListPanel.revalidate();
		itemListPanel.repaint();
	}

	private JPanel createTrackedItemBox(TrackedItem item)
	{
		// Main box with border layout
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1));
		box.setPreferredSize(new Dimension(42, 42));
		box.setToolTipText(item.getItemName());

		// Center panel for icon with text overlays
		JPanel centerPanel = new JPanel(null); // Null layout for absolute positioning
		centerPanel.setOpaque(false);
		centerPanel.setPreferredSize(new Dimension(40, 38));

		// Current amount (top-left corner) - add first so it's on top
		JLabel currentLabel = new JLabel(String.valueOf(item.getCurrentAmount()));
		currentLabel.setForeground(Color.YELLOW);
		currentLabel.setFont(FontManager.getRunescapeSmallFont());
		currentLabel.setBounds(1, -1, 38, 15);
		centerPanel.add(currentLabel);

		// Goal amount (bottom-right corner) - add second so it's on top
		JLabel goalLabel = new JLabel(String.valueOf(item.getGoalAmount()));
		goalLabel.setForeground(Color.WHITE);
		goalLabel.setFont(FontManager.getRunescapeSmallFont());
		goalLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		goalLabel.setBounds(0, 24, 40, 15);
		centerPanel.add(goalLabel);

		// Icon (centered) - add last so it's in the background
		JLabel iconLabel = new JLabel();
		AsyncBufferedImage itemImage = itemManager.getImage(item.getItemId());
		itemImage.addTo(iconLabel);
		iconLabel.setBounds(4, 3, 32, 32);
		centerPanel.add(iconLabel);

		box.add(centerPanel, BorderLayout.CENTER);

		// Progress bar at bottom
		JPanel progressBar = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				int width = getWidth();
				int height = getHeight();

				// Calculate progress percentage
				float progress = Math.min(1.0f, (float) item.getCurrentAmount() / item.getGoalAmount());

				// Determine color based on progress
				Color barColor;
				if (progress < 0.33f)
				{
					barColor = new Color(200, 0, 0); // Red
				}
				else if (progress < 0.67f)
				{
					barColor = new Color(255, 165, 0); // Orange
				}
				else if (progress < 1.0f)
				{
					barColor = new Color(255, 200, 0); // Yellow
				}
				else
				{
					barColor = new Color(0, 200, 0); // Green
				}

				// Draw progress bar
				int barWidth = (int) (width * progress);
				g.setColor(barColor);
				g.fillRect(0, 0, barWidth, height);
			}
		};
		progressBar.setPreferredSize(new Dimension(42, 2));
		progressBar.setOpaque(false);
		box.add(progressBar, BorderLayout.SOUTH);

		// Click handlers
		MouseAdapter clickHandler = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					showContextMenu(e, item);
				}
				else if (e.getClickCount() == 2)
				{
					openEditDialog(item);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				box.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};

		box.addMouseListener(clickHandler);
		centerPanel.addMouseListener(clickHandler);

		return box;
	}

	private void showContextMenu(MouseEvent e, TrackedItem item)
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem editItem = new JMenuItem("Edit Goal");
		editItem.addActionListener(ev -> openEditDialog(item));
		menu.add(editItem);
		
		JMenuItem deleteItem = new JMenuItem("Remove");
		deleteItem.addActionListener(ev -> removeTrackedItem(item));
		menu.add(deleteItem);

		menu.show(e.getComponent(), e.getX(), e.getY());
	}


	private void openEditDialog(TrackedItem item)
	{
		JTextField goalField = new JTextField(String.valueOf(item.getGoalAmount()), 10);

		JPanel dialogPanel = new JPanel(new GridLayout(1, 2, 5, 5));
		dialogPanel.add(new JLabel("New Goal Amount:"));
		dialogPanel.add(goalField);

		int result = JOptionPane.showConfirmDialog(
			this,
			dialogPanel,
			"Edit Goal for " + item.getItemName(),
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);

		if (result == JOptionPane.OK_OPTION)
		{
			try
			{
				int newGoal = Integer.parseInt(goalField.getText());
				if (newGoal <= 0)
				{
					JOptionPane.showMessageDialog(this, "Goal must be positive.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				item.setGoalAmount(newGoal);
				plugin.saveTrackedItems(trackedItems);
				rebuild();
			}
			catch (NumberFormatException ex)
			{
				JOptionPane.showMessageDialog(this, "Invalid number format.", "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
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

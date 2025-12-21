package com.resourcetracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ResourceTrackerPanel extends PluginPanel implements Scrollable
{
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
    // handles amount input like "100k", "2M"
    static class QuantityDocumentFilter extends DocumentFilter {
        private final Pattern pattern = Pattern.compile("\\d*[kKmMbB]?");

        private boolean test(String text) {
            return pattern.matcher(text).matches();
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.insert(offset, string);

            if (test(sb.toString())) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.replace(offset, offset + length, text);

            if (test(sb.toString())) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }

    private final ResourceTrackerPlugin plugin;
    private final ItemManager itemManager;
    private final ChatboxPanelManager chatboxPanelManager;

    private final JPanel itemListPanel;
    private final JPanel searchResultsPanel;
    private final JPanel contentWrapper;
    private final JScrollPane searchScrollPane;
    private final JScrollPane itemScrollPane;
    private final IconTextField searchBar;
    private final JTextField categoryNameField;
    private final List<TrackedItem> trackedItems = new CopyOnWriteArrayList<>();
    private final List<CategoryBox> categoryBoxes = new ArrayList<>();
    private String selectedCategory = null;
    private static final int MAX_SEARCH_RESULTS = 50;
    private Timer searchDebounceTimer;


    public ResourceTrackerPanel(ResourceTrackerPlugin plugin, ItemManager itemManager, ChatboxPanelManager chatboxPanelManager)
    {
        super(false);
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.chatboxPanelManager = chatboxPanelManager;

        setBorder(new EmptyBorder(6, 6, 6, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Search area - always visible at top
        JPanel searchAreaPanel = new JPanel();
        searchAreaPanel.setLayout(new BoxLayout(searchAreaPanel, BoxLayout.Y_AXIS));
        searchAreaPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        searchAreaPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR.darker()),
                new EmptyBorder(5, 5, 5, 5)
        ));
        add(searchAreaPanel, BorderLayout.NORTH);

        // Category name input field
        categoryNameField = new JTextField();
        categoryNameField.setPreferredSize(new Dimension(0, 30));
        categoryNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryNameField.setForeground(Color.GRAY);
        categoryNameField.setText("Add category...");
        categoryNameField.setCaretColor(Color.WHITE);

        ((AbstractDocument) categoryNameField.getDocument()).setDocumentFilter(new DocumentFilter() {
            private static final int MAX_LENGTH = 30;

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if ((fb.getDocument().getLength() + string.length()) <= MAX_LENGTH) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if ((fb.getDocument().getLength() + (text != null ? text.length() : 0) - length) <= MAX_LENGTH) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });

        categoryNameField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (categoryNameField.getText().equals("Add category..."))
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
                    categoryNameField.setText("Add category...");
                    categoryNameField.setForeground(Color.GRAY);
                }
            }
        });
        categoryNameField.addActionListener(e ->
        {
            String categoryName = categoryNameField.getText().trim();
            if (!categoryName.isEmpty() && !categoryName.equals("Add category..."))
            {
                createNewCategory(categoryName);
                categoryNameField.setText("Add category...");
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
        searchBar.setEnabled(false); // Disabled by default - only enabled when a category is selected
        searchBar.setEditable(false); // Prevent typing when disabled
        searchBar.setToolTipText("Select a category to add items");
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

        // Add key listener for Esc key
        searchBar.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    searchBar.setText("");
                    onSearchChanged();
                }
            }
        });

        searchAreaPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        searchAreaPanel.add(searchBar);


        // Wrapper panel for content (search results OR tracked items)
        contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(contentWrapper, BorderLayout.CENTER);

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

        JPanel itemPanelWrapper = new JPanel(new BorderLayout());
        itemPanelWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemPanelWrapper.add(itemListPanel, BorderLayout.NORTH);


        itemScrollPane = new JScrollPane(itemPanelWrapper);
        itemScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        itemScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        itemScrollPane.setBorder(null);


        contentWrapper.add(itemScrollPane, BorderLayout.CENTER);

        // Add keybinding for focusing the search bar
        KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "focusSearch");
        getActionMap().put("focusSearch", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                searchBar.requestFocusInWindow();
            }
        });
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
        plugin.addTrackedItem(item);
    }

    public void removeTrackedItem(int itemId, String category)
    {
        plugin.removeTrackedItem(itemId, category);
    }


    public List<TrackedItem> getTrackedItems()
    {
        return new ArrayList<>(plugin.getTrackedItems().values());
    }

    public void loadItems(java.util.Collection<TrackedItem> items)
    {
        trackedItems.clear();
        trackedItems.addAll(items);
        rebuildTrackedItems();
    }

    public void resetPanel()
    {
        SwingUtilities.invokeLater(() -> {
            trackedItems.clear();
            selectedCategory = null;
            searchBar.setEnabled(false); // No category selected, so disable search
            searchBar.setEditable(false); // Prevent typing when disabled
            searchBar.setToolTipText("Select a category to add items");

            categoryNameField.setText("Add category...");
            categoryNameField.setForeground(Color.GRAY);

            rebuildTrackedItems();
        });
    }

    public void rebuild()
    {
        loadItems(plugin.getTrackedItems().values());
    }

    public void setSelectedCategory(String category)
    {
        // Mark the selected category (for adding items), don't collapse others
        selectedCategory = category;

        if (category == null)
        {
            // Deselect all
            searchBar.setEnabled(false);
            searchBar.setEditable(false); // Prevent typing when disabled
            searchBar.setText(""); // Clear search text when deselecting
            searchBar.setToolTipText("Select a category to add items");

            for (CategoryBox box : categoryBoxes)
            {
                box.setSelected(false);
            }

            // Show tracked items view (not search results)
            contentWrapper.removeAll();
            contentWrapper.add(itemScrollPane, BorderLayout.CENTER);
            contentWrapper.revalidate();
            contentWrapper.repaint();
        }
        else
        {
            // Select specific category
            searchBar.setEnabled(true);
            searchBar.setEditable(true); // Allow typing when enabled
            searchBar.setToolTipText("Search items to add to " + category);

            // Update visual selection state for all categories
            for (CategoryBox box : categoryBoxes)
            {
                box.setSelected(box.getCategoryName().equals(category));
            }
        }
    }

    public void deleteCategory(String categoryName)
    {
        // Get items from plugin and remove those in this category
        List<TrackedItem> itemsToRemove = plugin.getTrackedItems().values().stream()
                .filter(item -> item.getCategory().equals(categoryName))
                .collect(Collectors.toList());

        itemsToRemove.forEach(item -> plugin.removeTrackedItem(item.getItemId(), item.getCategory()));

        // Remove from category order
        plugin.removeCategory(categoryName);

        // Clear selected category if it's the one being deleted
        if (categoryName.equals(selectedCategory))
        {
            selectedCategory = null;
            searchBar.setEnabled(false); // No category selected after deletion
            searchBar.setEditable(false); // Prevent typing when disabled
            searchBar.setToolTipText("Select a category to add items");
        }

		rebuildTrackedItems();
	}

	public void resetCategoryCounts(String categoryName)
	{
		// Get all items in this category and reset their counts
		plugin.getTrackedItems().values().stream()
			.filter(item -> item.getCategory().equals(categoryName))
			.forEach(item -> {
				// Reset current amount to 0
				item.setCurrentAmount(0);
				// Clear all container quantities
				item.getContainerQuantities().clear();
			});

		// Save the changes
		plugin.saveData();

		// Rebuild UI to show the reset counts
		rebuildTrackedItems();

		plugin.sendChatMessage("All item counts reset for category '" + categoryName + "'.");
	}

	public void renameCategory(String oldName, String newName)
	{
        // Validate new name
        if (newName == null || newName.trim().isEmpty())
        {
            plugin.sendChatMessage("Category name cannot be empty.");
            return;
        }

        String trimmedNewName = newName.trim();

        // Check if a category with the new name already exists
        boolean exists = plugin.getTrackedItems().values().stream()
                .anyMatch(item -> item.getCategory().equalsIgnoreCase(trimmedNewName) && !item.getCategory().equals(oldName));

        if (exists)
        {
            plugin.sendChatMessage("A category with that name already exists.");
            return;
        }

        // Update all items in the category to use the new name
        plugin.getTrackedItems().values().stream()
                .filter(item -> item.getCategory().equals(oldName))
                .forEach(item -> item.setCategory(trimmedNewName));

        // Update the category order tracking
        plugin.renameCategory(oldName, trimmedNewName);

        // Update selected category if it was the renamed one
        if (oldName.equals(selectedCategory))
        {
            selectedCategory = trimmedNewName;
            searchBar.setToolTipText("Search items to add to " + trimmedNewName);
        }

        rebuildTrackedItems();
        plugin.sendChatMessage("Category renamed to '" + trimmedNewName + "'.");
    }

    public void exportCategory(String categoryName)
    {
        List<TrackedItem> categoryItems = plugin.getTrackedItems().values().stream()
                .filter(item -> item.getCategory().equals(categoryName))
                .collect(Collectors.toList());

        if (categoryItems.isEmpty())
        {
            return;
        }

        // Create a new list of items with amounts reset to 0 for export
        List<TrackedItem> itemsForExport = new ArrayList<>();
        for (TrackedItem originalItem : categoryItems)
        {
            TrackedItem exportItem = new TrackedItem(
                originalItem.getItemId(),
                originalItem.getItemName(),
                originalItem.getGoalAmount(),
                originalItem.getCategory()
            );
            // The constructor already sets currentAmount to 0, but this is explicit
            exportItem.setCurrentAmount(0);
            itemsForExport.add(exportItem);
        }

        String json = plugin.getGson().toJson(itemsForExport);
        final StringSelection stringSelection = new StringSelection(json);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    public void importCategoryFromClipboard(String targetCategory)
    {
        final String clipboardText;
        try
        {
            clipboardText = Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .getData(DataFlavor.stringFlavor)
                .toString();
        }
        catch (IOException | UnsupportedFlavorException ex)
        {
            log.warn("Error reading clipboard", ex);
            plugin.sendChatMessage("Unable to read system clipboard.");
            return;
        }

        if (clipboardText == null || clipboardText.isEmpty())
        {
            plugin.sendChatMessage("You do not have any items copied in your clipboard.");
            return;
        }

        List<TrackedItem> importItems;
        try
        {
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<TrackedItem>>(){}.getType();
            importItems = plugin.getGson().fromJson(clipboardText, listType);
        }
        catch (com.google.gson.JsonSyntaxException | IllegalStateException e)
        {
            plugin.sendChatMessage("You do not have any valid categories copied in your clipboard.");
            return;
        }

        if (importItems == null || importItems.isEmpty())
        {
            plugin.sendChatMessage("You do not have any valid categories copied in your clipboard.");
            return;
        }

        plugin.getClientUi().requestFocus();

        chatboxPanelManager.openTextMenuInput("Are you sure you want to import " + importItems.size() + " items into '" + targetCategory + "'?")
                .option("Yes", () ->
                {
                    importCategory(importItems, targetCategory);
                    plugin.sendChatMessage(importItems.size() + " items were imported to " + targetCategory + ".");
                })
                .option("No", () -> {})
                .build();
    }

    public void importCategory(List<TrackedItem> importedItems, String targetCategory)
    {
        // Update category and add to list
        for (TrackedItem importedItem : importedItems)
        {
            if (importedItem != null && importedItem.getItemId() > 0 && importedItem.getItemName() != null)
            {
                importedItem.setCategory(targetCategory);
                // Reset current amount, as it's based on container state
                importedItem.setCurrentAmount(0);
                // Remove existing item if present before adding the new one
                plugin.removeTrackedItem(importedItem.getItemId(), targetCategory);
                plugin.addTrackedItem(importedItem);
            }
        }

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
        boolean exists = plugin.getTrackedItems().values().stream()
                .anyMatch(item -> item.getCategory().equalsIgnoreCase(trimmedCategoryName));

        if (exists)
        {
            return; // Silently ignore duplicates
        }

        // Register the category in the plugin's order tracking
        plugin.registerCategory(trimmedCategoryName);

        // Set as selected and focus search
        selectedCategory = trimmedCategoryName;
        searchBar.setEnabled(true);
        searchBar.setEditable(true); // Allow typing when enabled
        searchBar.requestFocusInWindow();

        // Force rebuild to show the empty category
        rebuildTrackedItems();
    }

    private void scheduleSearch()
    {
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

        if (categoryBoxes.isEmpty())
        {
            JLabel noCategory = new JLabel("Create a category to tracking items");
            noCategory.setForeground(Color.LIGHT_GRAY);
            noCategory.setBorder(new EmptyBorder(10, 10, 10, 10));
            searchResultsPanel.add(noCategory);
            searchResultsPanel.revalidate();
            searchResultsPanel.repaint();
            return;
        }

        String lowerQuery = query.toLowerCase();

        // Use ItemManager to search for items (tradeable items only)
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
        iconLabel.setToolTipText(itemDef.getName()); // Show full item name on hover
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
        ((AbstractDocument) goalField.getDocument()).setDocumentFilter(new QuantityDocumentFilter());

        JButton addButton = new JButton("+");
        goalField.addActionListener(e -> addButton.doClick());

        if (selectedCategory == null)
        {
            goalField.setEnabled(false);
            addButton.setEnabled(false);
            goalField.setToolTipText("Select a category to add an item");
            addButton.setToolTipText("Select a category to add an item");
        }
        else
        {
            goalField.setEnabled(true);
            addButton.setEnabled(true);
            goalField.setToolTipText("Enter goal amount");
            addButton.setToolTipText("Add to tracking");
        }

        addButton.setPreferredSize(new Dimension(35, 25));
        addButton.setMinimumSize(new Dimension(35, 25));
        addButton.setMaximumSize(new Dimension(35, 25));
        addButton.setToolTipText("Add to tracking");
        addButton.addActionListener(e -> {
            String goalText = goalField.getText().trim();
            Integer goal = null;

            try
            {
                if (!goalText.isEmpty())
                {
                    long parsedGoal = QuantityFormatter.parseQuantity(goalText);
                    if (parsedGoal > QuantityFormatter.getMaxStackSize())
                    {
                        parsedGoal = QuantityFormatter.getMaxStackSize();
                    }

                    if (parsedGoal > 0)
                    {
                        goal = (int) parsedGoal;
                    }
                }

                // Check if already tracked in the current category, if so, update it
                TrackedItem existing = plugin.getTrackedItems().get(itemDef.getId() + ":" + selectedCategory);
                if (existing != null && existing.getCategory().equals(selectedCategory))
                {
                    existing.setGoalAmount(goal);
                    plugin.saveData();
                    clearSearchAndRebuild();
                    return;
                }

                // If not tracked, add as a new item
			if (selectedCategory == null || selectedCategory.isEmpty())
			{
				return; // Should not happen
			}

			TrackedItem newItem = new TrackedItem(itemDef.getId(), itemDef.getName(), goal, selectedCategory);

			// Fetch and set GE and HA prices on client thread
			plugin.getClientThread().invoke(() -> {
				int gePrice = itemManager.getItemPrice(itemDef.getId());
				int haPrice = itemManager.getItemComposition(itemDef.getId()).getHaPrice();
				newItem.setGePrice(gePrice);
				newItem.setHaPrice(haPrice);

				// Add the item after prices are fetched
				SwingUtilities.invokeLater(() -> {
					addTrackedItem(newItem);
					clearSearchAndRebuild();
				});
			});
		}
		catch (NumberFormatException ex)
		{
			plugin.sendChatMessage("Please enter a valid number.");
		}
	});

        rightPanel.add(goalField);
        rightPanel.add(addButton);

        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private void clearSearchAndRebuild()
    {
        searchBar.setText("");
        contentWrapper.removeAll();
        contentWrapper.add(itemScrollPane, BorderLayout.CENTER);
        contentWrapper.revalidate();
        contentWrapper.repaint();
        rebuildTrackedItems();
    }


    private void rebuildTrackedItems()
    {
        SwingUtilities.invokeLater(() ->
        {
            // Save the current expanded/collapsed state of each category before clearing
            java.util.Map<String, Boolean> expandedStates = new java.util.HashMap<>();
            for (CategoryBox box : categoryBoxes)
            {
                expandedStates.put(box.getCategoryName(), !box.isCollapsed());
            }

            SwingUtil.fastRemoveAll(itemListPanel);
            categoryBoxes.clear();

            // Get items from plugin
            List<TrackedItem> items = new ArrayList<>(plugin.getTrackedItems().values());

            // Ensure all items have a category (for backward compatibility)
            items.forEach(item -> {
                if (item.getCategory() == null || item.getCategory().isEmpty())
                {
                    item.setCategory("Default");
                }
            });

            // Group items by category
            java.util.Map<String, List<TrackedItem>> itemsByCategory = items.stream()
                    .collect(Collectors.groupingBy(TrackedItem::getCategory));

            // Use plugin's category order for consistent ordering
            List<String> categoryNames = new ArrayList<>(plugin.getCategoryOrder());

            // Add any categories from items that aren't in the order yet (shouldn't happen but safety check)
            for (String category : itemsByCategory.keySet())
            {
                if (!categoryNames.contains(category))
                {
                    categoryNames.add(category);
                    plugin.registerCategory(category);
                }
            }

            // Add the currently selected (potentially new and empty) category to the end
            if (selectedCategory != null && !selectedCategory.isEmpty() && !categoryNames.contains(selectedCategory))
            {
                categoryNames.add(selectedCategory);
            }

            // Create all CategoryBoxes first (like LootTracker buildBox pattern)
            for (String categoryName : categoryNames)
            {
                List<TrackedItem> itemsForCategory = itemsByCategory.getOrDefault(categoryName, new ArrayList<>());
                CategoryBox categoryBox = new CategoryBox(categoryName, plugin, itemManager, this, chatboxPanelManager);
                categoryBoxes.add(categoryBox);

                // Build the box with its items
                categoryBox.rebuild(itemsForCategory);

                // Add to panel
                itemListPanel.add(categoryBox);
            }

            for (CategoryBox box : categoryBoxes)
            {
                boolean isThisSelected = box.getCategoryName().equals(selectedCategory);

                // Set selected state for visual indicator
                box.setSelected(isThisSelected);

                // Restore expanded/collapsed state, or expand if it's a new category and selected
                Boolean wasExpanded = expandedStates.get(box.getCategoryName());
                if (wasExpanded != null)
                {
                    // Restore previous state
                    if (wasExpanded)
                    {
                        box.expand();
                    }
                    else
                    {
                        box.collapse();
                    }
                }
                else
                {
                    // New category - expand only if selected
                    if (isThisSelected)
                    {
                        box.expand();
                    }
                    else
                    {
                        box.collapse();
                    }
                }
            }

            itemListPanel.revalidate();
        });
    }
}


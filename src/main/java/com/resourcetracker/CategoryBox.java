package com.resourcetracker;

import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CategoryBox extends JPanel
{
    private static final int ITEMS_PER_ROW = 5;

    private final String categoryName;
    private final ResourceTrackerPlugin plugin;
    private final ItemManager itemManager;
    private final ResourceTrackerPanel parentPanel;
    private final ChatboxPanelManager chatboxPanelManager;
    private final JPanel itemContainer = new JPanel();
    private final JPanel headerPanel = new JPanel();
    private final JLabel titleLabel = new JLabel();
    private final JPanel progressBarPanel = new JPanel();
    private final JLabel totalLabel = new JLabel();

    private List<TrackedItem> items;
    private boolean isSelected = false;

    public CategoryBox(String categoryName, ResourceTrackerPlugin plugin, ItemManager itemManager, ResourceTrackerPanel parentPanel, ChatboxPanelManager chatboxPanelManager)
    {
        this.categoryName = categoryName;
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.parentPanel = parentPanel;
        this.chatboxPanelManager = chatboxPanelManager;

        setLayout(new BorderLayout(0, 1));
        setBorder(new EmptyBorder(5, 0, 0, 0));

        // Header panel with vertical layout for title row and progress bar
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
        updateHeaderColor();

        // Title row (category name + total)
        JPanel titleRow = new JPanel();
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setOpaque(false);

        titleLabel.setText(categoryName);
        titleLabel.setFont(FontManager.getRunescapeSmallFont());
        titleLabel.setForeground(Color.WHITE);
        titleRow.add(titleLabel);
        titleRow.add(Box.createHorizontalGlue());

        totalLabel.setFont(FontManager.getRunescapeSmallFont());
        totalLabel.setForeground(new Color(200, 200, 200));
        titleRow.add(totalLabel);

        headerPanel.add(titleRow);

        // Progress bar panel
        progressBarPanel.setLayout(new BorderLayout());
        progressBarPanel.setOpaque(false);
        progressBarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        progressBarPanel.setPreferredSize(new Dimension(0, 4));
        progressBarPanel.setBorder(new EmptyBorder(3, 0, 0, 0));
        progressBarPanel.setVisible(false); // Initially hidden
        headerPanel.add(progressBarPanel);

        add(headerPanel, BorderLayout.NORTH);
        add(itemContainer, BorderLayout.CENTER);

        // Setup drag and drop for reordering categories
        setupDragAndDrop();

        // Make header clickable for selection and collapse/expand
        headerPanel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() == MouseEvent.BUTTON1)
                {
                    if (isCollapsed())
                    {
                        // Collapsed → Expand and select
                        expand();
                        parentPanel.setSelectedCategory(categoryName);
                    }
                    else if (isSelected)
                    {
                        // Expanded and selected → Collapse (deselect)
                        collapse();
                        parentPanel.setSelectedCategory(null);
                    }
                    else
                    {
                        // Expanded but not selected → Just select it
                        parentPanel.setSelectedCategory(categoryName);
                    }
                }
            }
        });

        // Add context menu for category management
        final JPopupMenu categoryPopup = new JPopupMenu();
        categoryPopup.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Category Mode Toggle
        boolean isInvOnly = plugin.isCategoryInventoryOnly(categoryName);
        JMenuItem toggleMode = new JMenuItem(isInvOnly ? "Track All Containers" : "Track Inventory Only");
        toggleMode.addActionListener(e -> {
            plugin.toggleCategoryInventoryOnly(categoryName);
        });
        categoryPopup.add(toggleMode);
        categoryPopup.addSeparator();

        JMenuItem expandItem = new JMenuItem("Expand");
        expandItem.addActionListener(e -> expand());
        categoryPopup.add(expandItem);

        JMenuItem collapseItem = new JMenuItem("Collapse");
        collapseItem.addActionListener(e -> collapse());
        categoryPopup.add(collapseItem);

        categoryPopup.addSeparator();

        JMenuItem exportCategory = new JMenuItem("Export Category");
        exportCategory.addActionListener(e -> parentPanel.exportCategory(categoryName));
        categoryPopup.add(exportCategory);

        JMenuItem importCategory = new JMenuItem("Import from Clipboard");
        importCategory.addActionListener(e -> parentPanel.importCategoryFromClipboard(categoryName));
        categoryPopup.add(importCategory);

        categoryPopup.addSeparator();

        JMenuItem resetCounts = new JMenuItem("Reset Item Counts");
        resetCounts.addActionListener(e -> {
            plugin.getClientUi().requestFocus();
            chatboxPanelManager.openTextMenuInput("Reset all item counts for '" + categoryName + "'?")
                    .option("Yes", () -> parentPanel.resetCategoryCounts(categoryName))
                    .option("No", () -> {})
                    .build();
        });
        categoryPopup.add(resetCounts);

        JMenuItem renameCategory = new JMenuItem("Rename Category");
        renameCategory.addActionListener(e -> {
            plugin.getClientUi().requestFocus();
            chatboxPanelManager.openTextInput("Enter new name for category:")
                    .onDone(newName -> {
                        if (newName != null && !newName.trim().isEmpty())
                        {
                            parentPanel.renameCategory(categoryName, newName.trim());
                        }
                    })
                    .build();
        });
        categoryPopup.add(renameCategory);

        JMenuItem deleteCategory = new JMenuItem("Delete Category");
        deleteCategory.addActionListener(e -> {
            plugin.getClientUi().requestFocus();
            chatboxPanelManager.openTextMenuInput("Delete category '" + categoryName + "' and all its items?")
                    .option("Yes", () -> parentPanel.deleteCategory(categoryName))
                    .option("No", () -> {})
                    .build();
        });
        categoryPopup.add(deleteCategory);

        headerPanel.setComponentPopupMenu(categoryPopup);
    }

    public void collapse()
    {
        if (!isCollapsed())
        {
            itemContainer.setVisible(false);
            parentPanel.revalidate();
            parentPanel.repaint();
        }
    }

    public void expand()
    {
        if (isCollapsed())
        {
            itemContainer.setVisible(true);
            parentPanel.revalidate();
            parentPanel.repaint();
        }
    }

    public boolean isCollapsed()
    {
        return !itemContainer.isVisible();
    }

    public void setSelected(boolean selected)
    {
        this.isSelected = selected;
        updateBorder();
        updateHeaderColor();
    }

    public boolean isSelected()
    {
        return isSelected;
    }

    private void updateBorder()
    {
        if (isSelected)
        {
            // Selected category has a colored border (Orange left strip)
            headerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(255, 144, 64)),
                    new EmptyBorder(7, 7, 7, 7)
            ));
        }
        else
        {
            // Non-selected category has default border
            headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
        }
        headerPanel.revalidate();
        headerPanel.repaint();
    }

    private void updateHeaderColor()
    {
        boolean isInvOnly = plugin.isCategoryInventoryOnly(categoryName);
        if (isInvOnly)
        {
            // Use a brownish color for inventory only mode
            // If selected, use a slightly lighter brown to indicate selection state
            // while preserving the "inventory only" color cue.
            headerPanel.setBackground(isSelected ? new Color(75, 55, 40) : new Color(60, 45, 30));
        }
        else
        {
            // Standard colors: Dark Gray when selected, Darker Gray when not
            headerPanel.setBackground(isSelected ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR.darker());
        }
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void rebuild(List<TrackedItem> items)
    {
        this.items = items;
        buildItems();
        updateHeaderStats();
        updateHeaderColor();
    }

    private void updateHeaderStats()
    {
        // Calculate totals
        long totalContribution = 0; // Capped at the goal for each item
        long totalGoal = 0;
        boolean hasGoals = false;

        if (items != null)
        {
            for (TrackedItem item : items)
            {
                if (item.getGoalAmount() != null)
                {
                    hasGoals = true;
                    int goal = item.getGoalAmount();
                    totalGoal += goal;

                    // Cap the contribution so extra items don't hide
                    // the fact that other items are still missing.
                    totalContribution += Math.min(item.getCurrentAmount(), goal);
                }
            }
        }

        // Update total label
        if (plugin.getConfig().showCategoryTotals() && hasGoals)
        {
            // Now shows "Items Collected / Total Goal" capped at 100%
            String totalText = QuantityFormatter.formatNumber(totalContribution) +
                    " / " + QuantityFormatter.formatNumber(totalGoal);
            totalLabel.setText(totalText);
            totalLabel.setVisible(true);
        }
        else
        {
            totalLabel.setVisible(false);
        }

        // Update progress bar
        if (plugin.getConfig().showCategoryProgress() && hasGoals && totalGoal > 0)
        {
            double progress = (double) totalContribution / totalGoal;
            updateProgressBar(progress);
            progressBarPanel.setVisible(true);
        }
        else
        {
            progressBarPanel.setVisible(false);
        }

        headerPanel.revalidate();
        headerPanel.repaint();
    }

    private void updateProgressBar(double progress)
    {
        progressBarPanel.removeAll();

        JPanel progressBar = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();

                // Background (dark gray)
                g2d.setColor(ColorScheme.DARK_GRAY_COLOR.darker());
                g2d.fillRect(0, 0, width, height);

                // Progress fill
                int progressWidth = (int) (width * Math.min(1.0, progress));
                if (progressWidth > 0)
                {
                    // Color based on progress
                    Color progressColor;
                    if (progress >= 1.0)
                    {
                        progressColor = new Color(0, 200, 0); // Green when complete
                    }
                    else if (progress >= 0.75)
                    {
                        progressColor = new Color(100, 200, 100); // Light green
                    }
                    else if (progress >= 0.5)
                    {
                        progressColor = new Color(255, 200, 0); // Yellow
                    }
                    else if (progress >= 0.25)
                    {
                        progressColor = new Color(255, 150, 0); // Orange
                    }
                    else
                    {
                        progressColor = new Color(200, 100, 100); // Light red
                    }

                    g2d.setColor(progressColor);
                    g2d.fillRect(0, 0, progressWidth, height);
                }
            }
        };

        progressBar.setOpaque(false);
        progressBarPanel.add(progressBar, BorderLayout.CENTER);
    }

    private void buildItems()
    {
        itemContainer.removeAll();

        if (items == null || items.isEmpty())
        {
            itemContainer.setVisible(true);
            itemContainer.setLayout(new BorderLayout());
            JLabel emptyLabel = new JLabel("No items");
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
            itemContainer.add(emptyLabel, BorderLayout.CENTER);
            itemContainer.setPreferredSize(new Dimension(0, 30));
            itemContainer.revalidate();
            return;
        }

        itemContainer.setVisible(true);

        // Calculate rows needed
        final int rowSize = ((items.size() % ITEMS_PER_ROW == 0) ? 0 : 1) + items.size() / ITEMS_PER_ROW;

        itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));


        for (TrackedItem item : items)
        {
            itemContainer.add(createTrackedItemBox(item));
        }

        // Fill remaining slots with empty panels
        int itemsAdded = items.size();
        for (int i = itemsAdded; i < rowSize * ITEMS_PER_ROW; i++)
        {
            final JPanel emptySlot = new JPanel();
            emptySlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            itemContainer.add(emptySlot);
        }

        itemContainer.revalidate();
    }

    private JPanel createTrackedItemBox(TrackedItem item)
    {
        JPanel slotContainer = new JPanel(new BorderLayout());
        slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // FIX: Ensure consistent sizing by adding a border in both cases
        if (item.isInventoryOnly())
        {
            // 1px colored border
            slotContainer.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_INPROGRESS_COLOR, 1));
        }
        else
        {
            // 1px transparent border to match size
            slotContainer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        }

        JPanel centerPanel = new JPanel(null);
        centerPanel.setOpaque(false);
        centerPanel.setPreferredSize(new Dimension(40, 40));

        // Current amount (top-left, yellow)
        JLabel currentLabel = new JLabel();
        QuantityFormatter.formatLabel(currentLabel, item.getCurrentAmount(), Color.YELLOW, false);
        currentLabel.setFont(FontManager.getRunescapeSmallFont());
        currentLabel.setBounds(1, 0, 38, 12);
        centerPanel.add(currentLabel);

        // Goal amount (bottom-right, white)
        if (item.getGoalAmount() != null)
        {
            JLabel goalLabel = new JLabel();
            QuantityFormatter.formatLabel(goalLabel, item.getGoalAmount(), Color.WHITE, true);
            goalLabel.setFont(FontManager.getRunescapeSmallFont());
            goalLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            goalLabel.setBounds(0, 28, 40, 12);
            centerPanel.add(goalLabel);
        }

        // Item icon (centered)
        JLabel iconLabel = new JLabel();
        iconLabel.setToolTipText(buildItemTooltip(item));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        AsyncBufferedImage itemImage = itemManager.getImage(item.getItemId());
        itemImage.addTo(iconLabel);
        iconLabel.setBounds(0, 0, 40, 40);
        centerPanel.add(iconLabel);

        slotContainer.add(centerPanel, BorderLayout.CENTER);

        // Progress bar at bottom
        if (item.getGoalAmount() != null && item.getGoalAmount() > 0)
        {
            JPanel progressBar = new JPanel()
            {
                @Override
                protected void paintComponent(Graphics g)
                {
                    super.paintComponent(g);
                    if (item.getGoalAmount() != null && item.getGoalAmount() > 0)
                    {
                        float progress = Math.min(1.0f, (float) item.getCurrentAmount() / item.getGoalAmount());
                        Color barColor = progress < 0.33f ? new Color(200, 0, 0)
                                : progress < 0.67f ? new Color(255, 165, 0)
                                : progress < 1.0f ? new Color(255, 200, 0)
                                : new Color(0, 200, 0);
                        g.setColor(barColor);
                        g.fillRect(0, 0, (int) (getWidth() * progress), getHeight());
                    }
                }
            };
            progressBar.setPreferredSize(new Dimension(40, 2));
            progressBar.setOpaque(false);
            slotContainer.add(progressBar, BorderLayout.SOUTH);
        }

        // Context menu
        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));

        JMenuItem editItem = new JMenuItem("Edit Goal");
        editItem.addActionListener(ev -> openEditDialog(item));
        popupMenu.add(editItem);

        // Inventory Only Toggle (Item Level)
        JMenuItem toggleInvOnly = new JMenuItem(item.isInventoryOnly() ? "Track All Inventories" : "Track Inventory Only");
        toggleInvOnly.addActionListener(ev -> {
            item.setInventoryOnly(!item.isInventoryOnly());
            plugin.saveData();
            plugin.updateTrackedItems(); // Force update to refresh count
        });
        popupMenu.add(toggleInvOnly);

        JMenuItem deleteItem = new JMenuItem("Remove");
        deleteItem.addActionListener(ev -> parentPanel.removeTrackedItem(item.getItemId(), item.getCategory()));
        popupMenu.add(deleteItem);

        setComponentPopupMenu(slotContainer, popupMenu);

        return slotContainer;
    }

    private void setComponentPopupMenu(Component component, final JPopupMenu popup)
    {
        if (component instanceof JComponent)
        {
            ((JComponent) component).setComponentPopupMenu(popup);
        }

        if (component instanceof Container)
        {
            for (Component c : ((Container) component).getComponents())
            {
                setComponentPopupMenu(c, popup);
            }
        }
    }

    private void openEditDialog(TrackedItem item)
    {
        chatboxPanelManager.openTextInput("Enter new goal for " + item.getItemName() + ":")
                .onDone((Consumer<String>) (input) -> {
                    SwingUtilities.invokeLater(() -> {
                        try
                        {
                            Integer newGoal = null;
                            if (input != null && !input.trim().isEmpty())
                            {
                                long parsedGoal = QuantityFormatter.parseQuantity(input.trim());
                                if (parsedGoal > QuantityFormatter.getMaxStackSize())
                                {
                                    parsedGoal = QuantityFormatter.getMaxStackSize();
                                }

                                if (parsedGoal > 0)
                                {
                                    newGoal = (int) parsedGoal;
                                }
                            }

                            item.setGoalAmount(newGoal);
                            plugin.saveData();
                            parentPanel.rebuild();
                        }
                        catch (NumberFormatException ex)
                        {
                            plugin.sendChatMessage("Please enter a valid number.");
                        }
                    });
                })
                .build();
    }

    private String buildItemTooltip(TrackedItem item)
    {
        StringBuilder tooltip = new StringBuilder("<html><b>").append(item.getItemName()).append("</b>");

        int quantity = item.getCurrentAmount();
        if (quantity > 0)
        {
            tooltip.append(" x ").append(QuantityFormatter.formatNumber(quantity));
        }

        // Add GE price if available
        if (item.getGePrice() > 0)
        {
            long totalGePrice = item.getTotalGePrice();
            tooltip.append("<br>GE: ").append(QuantityFormatter.formatNumber(totalGePrice));
            if (quantity > 1)
            {
                tooltip.append(" (").append(QuantityFormatter.formatNumber(item.getGePrice())).append(" ea)");
            }
        }

        // Add HA price if available (skip for coins and platinum tokens)
        if (item.getHaPrice() > 0 && item.getItemId() != 995 && item.getItemId() != 13204)
        {
            long totalHaPrice = item.getTotalHaPrice();
            tooltip.append("<br>HA: ").append(QuantityFormatter.formatNumber(totalHaPrice));
            if (quantity > 1)
            {
                tooltip.append(" (").append(QuantityFormatter.formatNumber(item.getHaPrice())).append(" ea)");
            }
        }

        // Add container breakdown
        Map<String, Integer> containers = item.getContainerQuantities();
        if (containers != null && !containers.isEmpty())
        {
            tooltip.append("<br><br><b>Locations:</b>");
            containers.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> tooltip.append("<br>")
                            .append(e.getKey())
                            .append(": ")
                            .append(String.format("%,d", e.getValue())));
        }

        tooltip.append("</html>");
        return tooltip.toString();
    }

    private void setupDragAndDrop()
    {
        // Make the header panel draggable
        headerPanel.setTransferHandler(new TransferHandler("text")
        {
            @Override
            public int getSourceActions(JComponent c)
            {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c)
            {
                return new StringSelection(categoryName);
            }
        });

        // Enable drag gesture
        headerPanel.addMouseMotionListener(new MouseAdapter()
        {
            @Override
            public void mouseDragged(MouseEvent e)
            {
                JComponent comp = (JComponent) e.getSource();
                TransferHandler handler = comp.getTransferHandler();
                handler.exportAsDrag(comp, e, TransferHandler.MOVE);
            }
        });

        // Create a shared drop handler for better responsiveness
        DropTargetAdapter dropHandler = new DropTargetAdapter()
        {
            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                try
                {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    String draggedCategoryName = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);

                    // Don't drop on self
                    if (!draggedCategoryName.equals(categoryName))
                    {
                        // Get the index of this category
                        List<String> order = plugin.getCategoryOrder();
                        int targetIndex = order.indexOf(categoryName);

                        // Move the dragged category to this position
                        if (targetIndex >= 0)
                        {
                            plugin.moveCategoryOrder(draggedCategoryName, targetIndex);
                        }
                    }

                    dtde.dropComplete(true);
                }
                catch (Exception e)
                {
                    dtde.dropComplete(false);
                }
                finally
                {
                    // Restore border
                    setBorder(new EmptyBorder(5, 0, 0, 0));
                    headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde)
            {
                // Visual feedback - highlight border when dragging over
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 144, 64), 2),
                        new EmptyBorder(3, 0, 0, 0)
                ));
                // Also highlight the header
                headerPanel.setBorder(BorderFactory.createCompoundBorder(
                        new EmptyBorder(0, 0, 0, 0),
                        new EmptyBorder(7, 7, 7, 7)
                ));
            }

            @Override
            public void dragExit(DropTargetEvent dte)
            {
                // Remove highlight when drag exits
                setBorder(new EmptyBorder(5, 0, 0, 0));
                headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
            }
        };

        // Setup drop target on both the category box AND the header panel
        // This ensures collapsed categories are easy to target
        new DropTarget(this, dropHandler);
        new DropTarget(headerPanel, dropHandler);
    }
}
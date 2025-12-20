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

		// Header panel
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
		headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		JLabel titleLabel = new JLabel();
		titleLabel.setText(categoryName);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(Color.WHITE);
		headerPanel.add(titleLabel);
		headerPanel.add(Box.createHorizontalGlue());

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
			applyDimmer(false, headerPanel);
			parentPanel.revalidate();
			parentPanel.repaint();
		}
	}

	public void expand()
	{
		if (isCollapsed())
		{
			itemContainer.setVisible(true);
			applyDimmer(true, headerPanel);
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
	}

	public boolean isSelected()
	{
		return isSelected;
	}

	private void updateBorder()
	{
		if (isSelected)
		{
			// Selected category has a colored border
			headerPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(255, 144, 64)), // Orange left border
				new EmptyBorder(7, 7, 7, 7)
			));
			headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
		else
		{
			// Non-selected category has default appearance
			headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
			headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		}
		headerPanel.revalidate();
		headerPanel.repaint();
	}

	private void applyDimmer(boolean brighten, JPanel panel)
	{
		for (Component component : panel.getComponents())
		{
			Color color = component.getForeground();
			component.setForeground(brighten ? color.brighter() : color.darker());
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

		// Calculate rows needed (like LootTracker)
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

		JMenuItem deleteItem = new JMenuItem("Remove");
		deleteItem.addActionListener(ev -> parentPanel.removeTrackedItem(item.getItemId()));
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

		Map<String, Integer> containers = item.getContainerQuantities();
		if (containers != null && !containers.isEmpty())
		{
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


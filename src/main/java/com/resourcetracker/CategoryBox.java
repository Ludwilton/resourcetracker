package com.resourcetracker;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class CategoryBox extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;

	private final String categoryName;
	private final ResourceTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final ResourceTrackerPanel parentPanel;
	private final JPanel itemContainer = new JPanel();
	private final JPanel headerPanel = new JPanel();
	private final JLabel titleLabel = new JLabel();

	private List<TrackedItem> items;

	public CategoryBox(String categoryName, ResourceTrackerPlugin plugin, ItemManager itemManager, ResourceTrackerPanel parentPanel)
	{
		this.categoryName = categoryName;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.parentPanel = parentPanel;

		setLayout(new BorderLayout(0, 1));
		setBorder(new EmptyBorder(5, 0, 0, 0));

		// Header panel
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
		headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		titleLabel.setText(categoryName);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(Color.WHITE);
		headerPanel.add(titleLabel);
		headerPanel.add(Box.createHorizontalGlue());

		add(headerPanel, BorderLayout.NORTH);
		add(itemContainer, BorderLayout.CENTER);

		// Make header clickable for collapse/expand
		headerPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					if (isCollapsed())
					{
						expand();
					}
					else
					{
						collapse();
					}
				}
			}
		});

		// Add context menu for category management
		final JPopupMenu categoryPopup = new JPopupMenu();
		categoryPopup.setBorder(new EmptyBorder(5, 5, 5, 5));

		JMenuItem deleteCategory = new JMenuItem("Delete Category");
		deleteCategory.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(
				this,
				"Delete category '" + categoryName + "' and all its items?",
				"Delete Category",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			if (result == JOptionPane.YES_OPTION)
			{
				parentPanel.deleteCategory(categoryName);
			}
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
			parentPanel.setSelectedCategory(categoryName);
			parentPanel.revalidate();
			parentPanel.repaint();
		}
	}

	public boolean isCollapsed()
	{
		return !itemContainer.isVisible();
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
		JLabel goalLabel = new JLabel();
		QuantityFormatter.formatLabel(goalLabel, item.getGoalAmount(), Color.WHITE, true);
		goalLabel.setFont(FontManager.getRunescapeSmallFont());
		goalLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		goalLabel.setBounds(0, 28, 40, 12);
		centerPanel.add(goalLabel);

		// Item icon (centered)
		JLabel iconLabel = new JLabel();
		iconLabel.setToolTipText(item.getItemName());
		iconLabel.setVerticalAlignment(SwingConstants.CENTER);
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage itemImage = itemManager.getImage(item.getItemId());
		itemImage.addTo(iconLabel);
		iconLabel.setBounds(0, 0, 40, 40);
		centerPanel.add(iconLabel);

		slotContainer.add(centerPanel, BorderLayout.CENTER);

		// Progress bar at bottom
		JPanel progressBar = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				float progress = Math.min(1.0f, (float) item.getCurrentAmount() / item.getGoalAmount());
				Color barColor = progress < 0.33f ? new Color(200, 0, 0)
					: progress < 0.67f ? new Color(255, 165, 0)
					: progress < 1.0f ? new Color(255, 200, 0)
					: new Color(0, 200, 0);
				g.setColor(barColor);
				g.fillRect(0, 0, (int) (getWidth() * progress), getHeight());
			}
		};
		progressBar.setPreferredSize(new Dimension(40, 2));
		progressBar.setOpaque(false);
		slotContainer.add(progressBar, BorderLayout.SOUTH);

		// Context menu
		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		slotContainer.setComponentPopupMenu(popupMenu);

		JMenuItem editItem = new JMenuItem("Edit Goal");
		editItem.addActionListener(ev -> openEditDialog(item));
		popupMenu.add(editItem);

		JMenuItem deleteItem = new JMenuItem("Remove");
		deleteItem.addActionListener(ev -> parentPanel.removeTrackedItem(item));
		popupMenu.add(deleteItem);

		return slotContainer;
	}

	private void openEditDialog(TrackedItem item)
	{
		JTextField goalField = new JTextField(String.valueOf(item.getGoalAmount()), 10);

		JPanel dialogPanel = new JPanel(new GridLayout(1, 2, 5, 5));
		dialogPanel.add(new JLabel("New Goal:"));
		dialogPanel.add(goalField);

		int result = JOptionPane.showConfirmDialog(
			this,
			dialogPanel,
			item.getItemName(),
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE
		);

		if (result == JOptionPane.OK_OPTION)
		{
			try
			{
				long newGoal = QuantityFormatter.parseQuantity(goalField.getText());
				if (newGoal > 0 && newGoal <= QuantityFormatter.getMaxStackSize())
				{
					item.setGoalAmount((int) newGoal);
					plugin.saveTrackedItems(parentPanel.getTrackedItems());
					parentPanel.rebuild();
				}
				else
				{
					JOptionPane.showMessageDialog(this, "Invalid goal amount.", "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
			catch (NumberFormatException ex)
			{
				JOptionPane.showMessageDialog(this, "Please enter a valid number.", "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}


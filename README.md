# Resource Tracker Plugin

## Overview

The Resource Tracker is a [RuneLite](https://runelite.net/) plugin that allows you to track your progress towards specific item goals. It is perfect for keeping track of resources for skilling, PvM, Questing, etc.

Items are organized into custom categories, and the plugin automatically updates the current amount of each tracked item based on the contents of your tracked containers (Bank, Inventory, Seed Vault, Potion Storage, etc.) which are toggleable in the plugin config.

## Features

- **Item Tracking**: Track any item with a current quantity and a goal quantity.
- **Multi-Container Support**: Automatically tracks items across your Bank, Inventory,Item Retrieval Service, Seed Vault, Group Storage, Sailing Boats, and more.
- **Inventory-Only Mode**: Toggle specific categories or items to only track what is currently in your inventory.
- **Category Management**: Organize your tracked items into custom categories (e.g., "Farming supplies", "Sailing prep").

- **Import/Export**: Share your setups with others via clipboard JSON export.

## How to Use

### Creating a Category
Type a name into the input field at the top of the panel and press `Enter`.

![Adding a category](./img/adding_category.webp)

### Adding an Item
1. Click a category header to select it (the header will highlight).
2. Use the search bar to find the item you want to track.
3. Enter a goal amount (e.g., `1000`, `25k`, `1.5m`) and press `Enter` or click the `+` button.

![Adding items](./img/adding_items.webp)

### Inventory Only Tracking
You can restrict tracking to **Inventory Only**.

**Category Level:**
Right-click a category header to toggle "Track Inventory Only". The category header will turn orange to indicate this mode.

![Category Inventory Toggle](./img/inventory_toggle.webp)

**Item Level:**
You can also toggle this for individual items by right-clicking the item panel.

![Item Inventory Toggle](./img/item_inv_toggle.webp)

### Managing Categories
- You can drag and drop categories to reorder them in the panel.
- You can expand/collapse categories by clicking a selected category or by Right-clicking.

![Reordering Categories](./img/reorder_toggle.webp)

### Managing Items
- **Edit a Goal**: Right-click an item and select "Edit Goal".
- **Remove an Item**: Right-click an item and select "Remove".
- **Delete a Category**: Right-click a category header and select "Delete Category".

### Import & Export
- **Export**: Right-click a category header and select "Export Category". The data is copied to your clipboard.
- **Import**: Copy a valid JSON string, then right-click a category header and select "Import from Clipboard".

## Support & Suggestions

If you have suggestions, want to contribute, or need support, feel free to join the Discord server [here.](https://discord.gg/dhCrh4whQW)
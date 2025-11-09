# Resource Tracker Plugin

## Overview

The Resource Tracker is a [RuneLite](https://runelite.net/) plugin that allows you to track your progress towards specific item goals. It's perfect for keeping track of resources for skilling, PvM, Questing etc.

Items are organized into custom categories, and the plugin automatically updates the current amount of each tracked item based on the contents of your bank.

![plugindemo](./img/plugindemo.mp4)

## Features

- **Item Tracking**: Track any item with a current quantity and a goal quantity.
- **Category Management**: Organize your tracked items into custom categories (e.g., "Farming supplies", "Sailing prep", "Desert Treasure II").
- **Automatic Bank Tracking**: Current item counts are automatically updated when you open your bank.
- **Progress Bars**: Each item has a visual progress bar to quickly see how close you are to your goal.
- **Import/Export**: Easily share your category setups with others by importing or exporting them as a JSON string via your clipboard.
- **Context Menus**: Right-click items and categories to easily edit, remove, import, or export.

## How to Use

1.  **Create a Category**:
    -   Type a name into the "New category name..." field at the top of the panel and press `Enter`.

2.  **Add an Item**:
    -   Click on a category header to expand and select it.
    -   Use the search bar to find the item you want to track.
    -   In the search results, enter a goal amount (e.g., `1000`, `25k`, `1.5m`) and press `Enter` or click the `+` button.

3.  **Manage Items and Categories**:
    -   **Edit a Goal**: Right-click an item and select "Edit Goal", or simply search for the item again and add it with a new goal amount.
    -   **Remove an Item**: Right-click an item and select "Remove".
    -   **Delete a Category**: Right-click a category header and select "Delete Category". This will remove the category and all items within it.

4.  **Import & Export**:
    -   **Export**: Right-click a category header and select "Export Category". The item data will be copied to your clipboard as a JSON string.
    -   **Import**: Copy a valid JSON string of item data, then right-click the desired category header and select "Import from Clipboard". This will add or update all items from the string into that category.

## Support & Suggestions

If you have suggestions, want to contribute or need support, feel free to join the Discord server [here.](https://discord.gg/dhCrh4whQW)

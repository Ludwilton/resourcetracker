package com.resourcetracker;

import java.awt.Color;
import javax.swing.JLabel;

public final class QuantityFormatter
{
	private static final int MAX_STACK_SIZE = 2_147_483_647;

	/**
	 * Formats a number into a stack-like string, e.g., 100k, 10M.
	 *
	 * @param quantity The number to format.
	 * @return A formatted string.
	 */
	public static String formatNumber(long quantity)
	{
		if (quantity >= 10_000_000)
		{
			return (quantity / 1_000_000) + "M";
		}
		if (quantity >= 100_000)
		{
			return (quantity / 1_000) + "K";
		}
		return String.valueOf(quantity);
	}

	/**
	 * Updates a JLabel with formatted text and color based on the quantity.
	 *
	 * @param label         The JLabel to update.
	 * @param quantity      The quantity to display.
	 * @param defaultColor  The default color for the text.
	 * @param highlightGreen If true, color will be green for quantities >= 10M.
	 */
	public static void formatLabel(JLabel label, long quantity, Color defaultColor, boolean highlightGreen)
	{
		label.setText(formatNumber(quantity));

		if (highlightGreen && quantity >= 10_000_000)
		{
			label.setForeground(Color.GREEN);
		}
		else
		{
			label.setForeground(defaultColor);
		}
	}

	/**
	 * Gets the maximum allowed value for an item stack.
	 *
	 * @return The maximum stack size.
	 */
	public static int getMaxStackSize()
	{
		return MAX_STACK_SIZE;
	}

	/**
	 * Parses a string with suffixes (e.g., 'k', 'm', 'b') into a long.
	 *
	 * @param text The string to parse.
	 * @return The parsed long value.
	 * @throws NumberFormatException if the string is not a valid number or format.
	 */
	public static long parseQuantity(String text) throws NumberFormatException
	{
		if (text == null || text.isEmpty())
		{
			throw new NumberFormatException("Input string is empty");
		}

		text = text.toLowerCase().trim();
		char lastChar = text.charAt(text.length() - 1);
		long multiplier = 1;

		if (Character.isLetter(lastChar))
		{
			switch (lastChar)
			{
				case 'k':
					multiplier = 1_000;
					break;
				case 'm':
					multiplier = 1_000_000;
					break;
				case 'b':
					multiplier = 1_000_000_000;
					break;
				default:
					throw new NumberFormatException("Invalid suffix: " + lastChar);
			}
			// Remove the suffix
			text = text.substring(0, text.length() - 1);
		}

		try
		{
			// Use double parsing to allow for decimals like "1.5m"
			double value = Double.parseDouble(text);
			long result = (long) (value * multiplier);

			// Check for overflow
			if (result < 0) {
				return Long.MAX_VALUE;
			}

			return result;
		}
		catch (NumberFormatException e)
		{
			throw new NumberFormatException("Invalid number format: " + text);
		}
	}
}

package com.resourcetracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

public class VerticalFlowLayout implements LayoutManager
{
	private final int vGap;

	public VerticalFlowLayout()
	{
		this(2);
	}

	public VerticalFlowLayout(int vGap)
	{
		this.vGap = vGap;
	}

	@Override
	public void addLayoutComponent(String name, Component comp)
	{
	}

	@Override
	public void removeLayoutComponent(Component comp)
	{
	}

	@Override
	public Dimension preferredLayoutSize(Container parent)
	{
		synchronized (parent.getTreeLock())
		{
			Dimension dim = new Dimension(0, 0);
			int nMembers = parent.getComponentCount();

			for (int i = 0; i < nMembers; i++)
			{
				Component m = parent.getComponent(i);
				if (m.isVisible())
				{
					Dimension d = m.getPreferredSize();
					dim.width = Math.max(dim.width, d.width);
					dim.height += d.height;
					if (i > 0)
					{
						dim.height += vGap;
					}
				}
			}

			Insets insets = parent.getInsets();
			dim.width += insets.left + insets.right;
			dim.height += insets.top + insets.bottom;

			return dim;
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container parent)
	{
		return preferredLayoutSize(parent);
	}

	@Override
	public void layoutContainer(Container parent)
	{
		synchronized (parent.getTreeLock())
		{
			Insets insets = parent.getInsets();
			int x = insets.left;
			int y = insets.top;
			int width = parent.getWidth() - (insets.left + insets.right);

			for (Component c : parent.getComponents())
			{
				if (c.isVisible())
				{
					Dimension d = c.getPreferredSize();
					c.setBounds(x, y, width, d.height);
					y += d.height + vGap;
				}
			}
		}
	}
}


package com.resourcetracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * A vertical flow layout.
 * <p>
 * This layout manager is similar to {@link java.awt.FlowLayout} but arranges
 * components vertically.
 */
public class VerticalFlowPanel extends JPanel
{
	private static class VerticalFlowLayout implements LayoutManager
	{
		private final int vgap;

		VerticalFlowLayout(int vgap)
		{
			this.vgap = vgap;
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
				int nmembers = parent.getComponentCount();

				for (int i = 0; i < nmembers; i++)
				{
					Component m = parent.getComponent(i);
					if (m.isVisible())
					{
						Dimension d = m.getPreferredSize();
						dim.width = Math.max(dim.width, d.width);
						dim.height += d.height + vgap;
					}
				}

				Insets insets = parent.getInsets();
				dim.width += insets.left + insets.right;
				dim.height += insets.top + insets.bottom;

				if (nmembers > 0)
				{
					dim.height -= vgap;
				}

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
						y += d.height + vgap;
					}
				}
			}
		}
	}

	public VerticalFlowPanel()
	{
		super(new VerticalFlowLayout(2));
	}
}

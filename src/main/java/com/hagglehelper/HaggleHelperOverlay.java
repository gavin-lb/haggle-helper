package com.hagglehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.google.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
public class HaggleHelperOverlay extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private HaggleHelperConfig config;

    @Inject
    private HaggleHelperPlugin plugin;

    private final TooltipManager tooltipManager;

    public enum OverlayMode
    {
        NONE,
        TRACKED,
        ALL
    }

    @Inject
    public HaggleHelperOverlay(TooltipManager tooltipManager)
    {
        this.tooltipManager = tooltipManager;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final Widget items = client.getWidget(InterfaceID.Shopside.ITEMS);
        if (items == null)
        {
            return null;
        }

        Widget[] children = items.getChildren();
        if (children == null)
        {
            return null;
        }

        if (config.overlayEnabled() != OverlayMode.NONE)
        {
            drawItemOverlay(graphics, children);
        }

        if (config.tooltipEnabled())
        {
            drawTooltip();
        }

        return null;
    }

    private void drawItemOverlay(Graphics2D graphics, Widget[] items)
    {
        FontMetrics fm = graphics.getFontMetrics();
        for (Widget itemWidget : items)
        {
            if (itemWidget == null || itemWidget.getItemId() <= 0)
            {
                continue;
            }

            final HighlightedItem highlightedItem = plugin.highlightedItems.get(itemWidget.getItemId());
            if (highlightedItem == null)
            {
                continue;
            }
            
            final Rectangle bounds = new Rectangle(itemWidget.getBounds());
            bounds.grow(1, 1);
            bounds.translate(-2, -2);
            
            final int maxProfit = highlightedItem.maxProfit;
            final int numProfitable = highlightedItem.numProfitable;
            final int profitThreshold = config.profitThreshold();
            final int bulkLossAllowance = config.bulkLossAllowance();
            final int profit10 = highlightedItem.profits.get(10);
            final int profit5 = highlightedItem.profits.get(5);
            final int profit1 = highlightedItem.profits.get(1);
            final Color color;

            if (maxProfit <= profitThreshold)
            {
                color = config.unprofitableColor();
            }
            else if (profit10 > 10*profitThreshold && profit10 > Math.max(profit5, profit1) && (numProfitable >= 10 || maxProfit - profit10 < bulkLossAllowance))
            {
                color = config.tenProfitableColor();
            }
            else if (profit5 > 5*profitThreshold && profit5 > profit1 && (numProfitable >= 5 || maxProfit - profit5 < bulkLossAllowance))
            {
                color = config.fiveProfitableColor();
            }
            else
            {
                color = config.oneProfitableColor();
            }

            if (config.showBoxFill()) 
            {
                graphics.setColor(new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    80
                ));
                graphics.fill(bounds);    
            }

            if (config.showBoxBorder())
            {
                graphics.setColor(color);
                graphics.draw(bounds);
            }

            if (config.showCurrentPrice())
            {
                final String priceText = config.shortenCurrentPrice() 
                    ? formatGp(highlightedItem.currentBuyPrice) 
                    : String.valueOf(highlightedItem.currentBuyPrice);

                OverlayUtil.renderTextLocation(
                    graphics,
                    new Point(
                        bounds.x + config.currentPriceX() - fm.stringWidth(priceText),
                        bounds.y + config.currentPriceY()
                    ),
                    priceText,
                    config.currentPriceColor()
                );
            }

            if (maxProfit > profitThreshold)
            {
                if (config.showNumber())
                {
                    OverlayUtil.renderTextLocation(
                        graphics,
                        new Point(bounds.x + config.numberX(), bounds.y + config.numberY()),
                        String.valueOf(highlightedItem.numProfitable),
                        config.numberColor()
                    );
                }

                if (config.showProfit())
                {
                    final String profitText = config.shortenProfit() 
                        ? formatGp(maxProfit) 
                        : String.valueOf(maxProfit);

                    OverlayUtil.renderTextLocation(
                        graphics,
                        new Point(
                            bounds.x + config.profitX() - fm.stringWidth(profitText),
                            bounds.y + config.profitY()
                        ),
                        profitText,
                        config.profitColor()
                    );
                }
            }
        }
    }

    private String formatGp(int gp)
    {
        return gp >= 1_000
            ? String.format("%.1fK", gp / 1000.0)
            : String.valueOf(gp);
    }

    private void drawTooltip()
    {
        
        if (client.isMenuOpen())
		{
			return;
		}

		MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
		final int last = menuEntries.length - 1;

        if (last < 0)
		{
			return;
		}

		MenuEntry menuEntry = menuEntries[last];
		final int itemId = menuEntry.getItemId();

        HighlightedItem highlightedItem = plugin.highlightedItems.get(itemId);
        if (highlightedItem == null )
        {
            return;
        }

        String menuOption = menuEntry.getOption().replaceAll("<.*>", "");
        final int sellAmount;
        try {
            sellAmount = Integer.parseInt(menuOption.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return;
        }
        final int profit = highlightedItem.profits.get(sellAmount);
        final int revenue = highlightedItem.revenues.get(sellAmount);
        final int profitThreshold = config.profitThreshold();
        final int bulkLossAllowance = config.bulkLossAllowance();
        final int loss = profit > profitThreshold && sellAmount > highlightedItem.numProfitable 
            ? highlightedItem.maxProfit - profit 
            : 0;
        final String profitColor = profit <= sellAmount*profitThreshold ? "ff0000" : "ffff00";
        final String lossColor = loss > bulkLossAllowance ? "ff0000" : "ffff00";
        final String lossText = loss > 0 ? String.format("<col=%s>(-%,d gp)</col>", lossColor, loss): "";
        tooltipManager.add(new Tooltip(
            String.format("%s revenue: <col=ffff00>%,d gp</col><br>%s profit: <col=%s>%,d gp</col> %s", 
            menuOption, revenue, menuOption, profitColor, profit, lossText)
        ));
    }
}

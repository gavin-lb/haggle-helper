package com.hagglehelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import com.google.inject.Inject;
import com.hagglehelper.HaggleHelperConfig.InterfaceMode;
import com.hagglehelper.HaggleHelperConfig.OverlayMode;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
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
    
    @Inject
    private HighlightedItemsManager highlightedItemsManager;

    @Inject
    private TrackedItemsManager trackedItemsManager;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private ItemManager itemManager;

    public HaggleHelperOverlay()
    {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final Widget items = client.getWidget(InterfaceID.Shopside.ITEMS);
        final Widget shopItems = client.getWidget(InterfaceID.Shopmain.ITEMS);
        if (items == null || shopItems == null)
        {
            return null;
        }

        final Widget[] itemWidgets = items.getChildren();
        final Widget[] shopItemWidgets = shopItems.getChildren();
        if (itemWidgets == null || shopItemWidgets == null)
        {
            return null;
        }

        if (config.overlayEnabled() != OverlayMode.NONE)
        {
            InterfaceMode mode = config.interfaceMode();

            if (mode.showInventory())
            {
                drawItemOverlay(graphics, itemWidgets, InterfaceMode.INVENTORY);
            }

            if (mode.showShop())
            {
                drawItemOverlay(graphics, shopItemWidgets, InterfaceMode.SHOP);
            }
        }

        if (config.tooltipEnabled() != OverlayMode.NONE)
        {
            drawTooltip();
        }

        return null;
    }

    private void drawItemOverlay(Graphics2D graphics, Widget[] items, InterfaceMode mode)
    {
        FontMetrics fm = graphics.getFontMetrics();
        for (Widget itemWidget : items)
        {
            if (itemWidget == null)
            {
                continue;
            }

            int itemId = itemWidget.getItemId();
            if (itemId <= 0
                    || config.overlayEnabled() == OverlayMode.TRACKED && !trackedItemsManager.isTrackedItemId(itemId)) 
            {
                continue;
            }

            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            if (!plugin.shop.isTradableWith(itemId) && !plugin.shop.isTradableWith(itemComposition.getLinkedNoteId())
                    || !itemComposition.isTradeable()) 
            {
                continue;
            }

            HighlightedItem highlightedItem = highlightedItemsManager.getOrCreate(itemId, mode);
            
            final Rectangle bounds = new Rectangle(itemWidget.getBounds());

            Dimension padding = config.boxPadding();
            bounds.grow(padding.width, padding.height);

            Dimension offset = config.boxOffset();
            bounds.translate(offset.width-2, offset.height-2);

            Color color = highlightedItem.color;

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
                final String currentPriceText = config.shortenCurrentPrice() 
                    ? formatGp(highlightedItem.currentPrice) 
                    : String.valueOf(highlightedItem.currentPrice);

                Dimension currentPriceOffset = config.currentPriceOffset();
                OverlayUtil.renderTextLocation(
                    graphics,
                    new Point(
                        bounds.x + currentPriceOffset.width - fm.stringWidth(currentPriceText),
                        bounds.y + currentPriceOffset.height
                    ),
                    currentPriceText,
                    config.currentPriceColor()
                );
            }

            if (highlightedItem.maxProfit > config.profitThreshold())
            {
                if (config.showNumber())
                {
                    Dimension numberOffset = config.numberOffset();
                    OverlayUtil.renderTextLocation(
                        graphics,
                        new Point(bounds.x + numberOffset.width, bounds.y + numberOffset.height),
                        String.valueOf(highlightedItem.numProfitable),
                        config.numberColor()
                    );
                }

                if (config.showProfit())
                {
                    final String profitText = config.shortenProfit() 
                        ? formatGp(highlightedItem.maxProfit) 
                        : String.valueOf(highlightedItem.maxProfit);

                    Dimension profitOffset = config.profitOffset();

                    OverlayUtil.renderTextLocation(
                        graphics,
                        new Point(
                            bounds.x + profitOffset.width - fm.stringWidth(profitText),
                            bounds.y + profitOffset.height
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
        if (config.tooltipEnabled() == OverlayMode.TRACKED && !trackedItemsManager.isTrackedItemId(itemId)) 
        {
            return;
        }
        
        Widget widget = menuEntry.getWidget();
        if (widget == null)
        {
            return;
        }

        InterfaceMode mode = widget.getId() == InterfaceID.Shopmain.ITEMS
            ? InterfaceMode.SHOP
            : InterfaceMode.INVENTORY;
        if (mode != config.interfaceMode() && config.interfaceMode() != InterfaceMode.BOTH)
        {
            return;
        }

        HighlightedItem item = highlightedItemsManager.getOrCreate(itemId, mode);
        if (item == null)
        {
            return;
        }

        String menuOption = menuEntry.getOption().replaceAll("<.*>", "");
        final int transactionAmount;
        try {
            transactionAmount = Integer.parseInt(menuOption.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return;
        }

        final int stockAmount;
        final int amount;
        final int profit;
        final int revenue;
        switch (item.mode) {
            case INVENTORY:
                stockAmount = widget.getItemQuantity();
                amount = Math.min(transactionAmount, stockAmount);
                profit = plugin.shop.getProfitSellTo(amount, item);
                revenue = plugin.shop.getRevenueSellTo(amount, item);
                break;
                
            default:
                stockAmount = plugin.shop.getStock(item);
                amount = Math.min(transactionAmount, stockAmount);
                profit = plugin.shop.getProfitBuyFrom(amount, item);
                revenue = plugin.shop.getRevenueBuyFrom(amount, item);
        }

        final int profitThreshold = config.profitThreshold();
        final int bulkLossAllowance = config.bulkLossAllowance();

        final int loss = profit > profitThreshold && transactionAmount > item.numProfitable 
            ? item.maxProfit - profit 
            : 0;

        final String option = transactionAmount != amount 
            ? String.format("%s <col=ff0000>(%d)</col>", menuOption, amount) 
            : menuOption;

        final String profitColor = profit <= transactionAmount*profitThreshold 
            ? "ff0000" 
            : "ffff00";

        final String lossColor = loss > bulkLossAllowance 
            ? "ff0000" 
            : "ffff00";

        final String lossText = loss > 0 
            ? String.format("<col=%s>(-%,d gp)</col>", lossColor, loss)
            : "";

        final String noun = item.mode == InterfaceMode.SHOP 
            ? "cost" 
            : "value";

        tooltipManager.add(new Tooltip(
            String.format("%s %s: <col=ffff00>%,d gp</col><br>%s profit: <col=%s>%,d gp</col> %s", 
            option, noun, revenue, option, profitColor, profit, lossText)
        ));
    }
}

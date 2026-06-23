package com.hagglehelper;

import java.awt.Color;

import com.hagglehelper.HaggleHelperOverlay.OverlayMode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(HaggleHelperConfig.GROUP)
public interface HaggleHelperConfig extends Config
{
	String GROUP = "hagglehelper";

	@ConfigItem(
		keyName = "overlayEnabled",
		name = "Overlay",
		description = "Which items overlays should be enabled for", 
		position = 0
	)
	default OverlayMode overlayEnabled()
	{
		return OverlayMode.TRACKED;
	}

	@ConfigItem(
		keyName = "tooltipEnabled",
		name = "Tooltip",
		description = "Whether item tooltips are enabled", 
		position = 1
	)
	default boolean tooltipEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "profitInfoEnabled",
		name = "Profit info",
		description = "Whether the profit info panel is enabled", 
		position = 2
	)
	default boolean profitInfoEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoCost",
		name = "Auto cost from GE",
		description = "Automatically populate cost with the Grand Exchange price when adding an item", 
		position = 3
	)
	default boolean autoCost()
	{
		return true;
	}

    @ConfigItem(
		keyName = "profitThreshold",
		name = "Profit threshold",
		description = "The per-item profit margin threshold, in gp, that an item must be exceed to be considered profitable", 
		position = 4
	)
	default int profitThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "blockUnprofitable",
		name = "Block unprofitable",
		description = "Whether to block any unprofitable transactions", 
		position = 5
	)
	default boolean blockUnprofitable()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bulkLossAllowance",
		name = "Bulk loss allowance",
		description = "The allowed profit loss, in gp, before a bulk transaction is blocked (eg. if less than 10 items"
		+ " are profitable the bulk \"Sell 10\" option is only blocked if lost potential profit exceeds allowance)", 
		position = 6
	)
	default int bulkLossAllowance()
	{
		return 50;
	}


	@ConfigSection(
        name = "Overlay - Box",
        description = "Overlay box display settings",
		position = 7
    )
    String boxSection = "boxSection";

	@ConfigItem(
		keyName = "showBoxFill",
		name = "Show fill",
		description = "Whether to show the fill of the box on the overlay",
		section = boxSection,
		position = 0
	)
	default boolean showBoxFill()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBoxBorder",
		name = "Show border",
		description = "Whether to show the border of the box on the overlay",
		section = boxSection,
		position = 0
	)
	default boolean showBoxBorder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tenProfitableColor",
		name = "Sell 10 color",
		description = "Color used for the overlay when \"Sell 10\" of an item is profitable",
		section = boxSection,
		position = 1
	)
	default Color tenProfitableColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "fiveProfitableColor",
		name = "Sell 5 color",
		description = "Color used for the overlay when \"Sell 5\" of an item is profitable",
		section = boxSection,
		position = 2
	)
	default Color fiveProfitableColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "oneProfitableColor",
		name = "Sell 1 color",
		description = "Color used for the overlay when \"Sell 1\" of an item is profitable",
		section = boxSection,
		position = 3
	)
	default Color oneProfitableColor()
	{
		return Color.decode("#FF8800");
	}

	@ConfigItem(
		keyName = "unprofitableColor",
		name = "Unprofitable color",
		description = "Color used for the overlay when item is unprofitable",
		section = boxSection,
		position = 4
	)
	default Color unprofitableColor()
	{
		return Color.RED;
	}
	
	@ConfigSection(
        name = "Overlay - Amount Text",
        description = "Profitable amount text overlay settings", 
		position = 8
    )
    String numberSection = "numberSection";

	@ConfigItem(
		keyName = "showNumber",
		name = "Show profitable amount",
		description = "Whether to show the number of items that can be sold profitably on the overlay",
		section = numberSection,
		position = 0
	)
	default boolean showNumber()
	{
		return true;
	}

	@ConfigItem(
		keyName = "numberX",
		name = "X offset",
		description = "Horizontal offset in pixels of the number text overlay",
		section = numberSection,
		position = 1
	)
	default int numberX()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "numberY",
		name = "Y offset",
		description = "Vertical offset in pixels of the number text overlay",
		section = numberSection,
		position = 2
	)
	default int numberY()
	{
		return 34;
	}
	
	@ConfigItem(
		keyName = "numberColor",
		name = "Color",
		description = "Color used for number text overlay",
		section = numberSection,
		position = 3
	)
	default Color numberColor()
	{
		return Color.WHITE;
	}
	
	@ConfigSection(
        name = "Overlay - Profit Text",
        description = "Profit overlay display settings",
		position = 9
    )
    String profitSection = "profitSection";

	@ConfigItem(
		keyName = "showProfit",
		name = "Show profit",
		description = "Whether to show the total potential profit on the overlay",
		section = profitSection,
		position = 0
	)
	default boolean showProfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "profitX",
		name = "X offset",
		description = "Horizontal offset in pixels of the profit text overlay",
		section = profitSection,
		position = 1
	)
	default int profitX()
	{
		return 38;
	}

	@ConfigItem(
		keyName = "profitY",
		name = "Y offset",
		description = "Vertical offset in pixels of the profit text overlay",
		section = profitSection,
		position = 2
	)
	default int profitY()
	{
		return 34;
	}

	@ConfigItem(
		keyName = "profitColor",
		name = "Color",
		description = "Color used for profit text overlay",
		section = profitSection,
		position = 3
	)
	default Color profitColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "shortenProfit",
		name = "Shorten",
		description = "Whether to shorten the profit text when appropriate, eg. 54321 becomes 54.3k",
		section = profitSection,
		position = 3
	)
	default boolean shortenProfit()
	{
		return true;
	}

	@ConfigSection(
        name = "Overlay - Current Price Text",
        description = "Current price overlay display settings",
		position = 10
    )
    String currentPriceSection = "currentPriceSection";

	@ConfigItem(
		keyName = "showCurrentPrice",
		name = "Show current price",
		description = "Whether to show the current price on the overlay",
		section = currentPriceSection,
		position = 0
	)
	default boolean showCurrentPrice()
	{
		return false;
	}

	@ConfigItem(
		keyName = "currentPriceX",
		name = "X offset",
		description = "Horizontal offset in pixels of the current price text overlay",
		section = currentPriceSection,
		position = 1
	)
	default int currentPriceX()
	{
		return 38;
	}

	@ConfigItem(
		keyName = "currentPriceY",
		name = "Y offset",
		description = "Vertical offset in pixels of the current price text overlay",
		section = currentPriceSection,
		position = 2
	)
	default int currentPriceY()
	{
		return 24;
	}

	@ConfigItem(
		keyName = "currentPriceColor",
		name = "Color",
		description = "Color used for current price text overlay",
		section = currentPriceSection,
		position = 3
	)
	default Color currentPriceColor()
	{
		return Color.YELLOW;
	}
	
	@ConfigItem(
		keyName = "shortenCurrentPrice",
		name = "Shorten",
		description = "Whether to shorten the current price text when appropriate, eg. 54321 becomes 54.3k",
		section = currentPriceSection,
		position = 3
	)
	default boolean shortenCurrentPrice()
	{
		return true;
	}

	@ConfigItem(
            keyName = "itemPricesJson",
            name = "Item prices (JSON)",
            description = "Internal storage",
            hidden = true
    )
    default String itemCostsJson()
    {
        return "{}";
    }

    @ConfigItem(
            keyName = "itemPricesJson",
            name = "Item prices (JSON)",
            description = "Internal storage",
            hidden = true
    )
    void setItemCostsJson(String json);
}

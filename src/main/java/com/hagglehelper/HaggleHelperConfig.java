package com.hagglehelper;

import java.awt.Color;
import java.awt.Dimension;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(HaggleHelperConfig.GROUP)
public interface HaggleHelperConfig extends Config
{
	String GROUP = "hagglehelper";

	enum OverlayMode
    {
        NONE,
        TRACKED,
        ALL
    }

	enum InterfaceMode
	{
		INVENTORY,
		SHOP, 
		BOTH;

		boolean showInventory()
		{
			return this == INVENTORY || this == BOTH;
		}

		boolean showShop()
		{
			return this == SHOP || this == BOTH;
		}
	}
	
	//#region root section
	@ConfigItem(
		keyName = "interfaceMode",
		name = "Interface mode",
		description = "Which interface(s) the plugin should be enabled for", 
		position = 0
	)
	default InterfaceMode interfaceMode()
	{
		return InterfaceMode.BOTH;
	}	
	
	@ConfigItem(
		keyName = "overlayEnabled",
		name = "Overlay",
		description = "Which items overlays should be enabled for (untracked items use their current GE price)", 
		position = 1
	)
	default OverlayMode overlayEnabled()
	{
		return OverlayMode.TRACKED;
	}

	@ConfigItem(
		keyName = "tooltipEnabled",
		name = "Tooltip",
		description = "Which items tooltips should be enabled for (untracked items use their current GE price)", 
		position = 2
	)
	default OverlayMode tooltipEnabled()
	{
		return OverlayMode.TRACKED;
	}

	@ConfigItem(
		keyName = "profitInfoEnabled",
		name = "Profit info",
		description = "Whether the profit info panel is enabled", 
		position = 3
	)
	default boolean profitInfoEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoCost",
		name = "Auto cost from GE",
		description = "Automatically populate cost with the Grand Exchange price when adding an item", 
		position = 4
	)
	default boolean autoCost()
	{
		return true;
	}

    @ConfigItem(
		keyName = "profitThreshold",
		name = "Profit threshold",
		description = "The per-item profit margin threshold, in gp, that an item must be exceed to be considered profitable", 
		position = 5
	)
	default int profitThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "blockUnprofitable",
		name = "Block unprofitable",
		description = "Whether to block any unprofitable transactions", 
		position = 6
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
		position = 7
	)
	default int bulkLossAllowance()
	{
		return 50;
	}

	//#region Overlay box section
	@ConfigSection(
        name = "Overlay - Box",
        description = "Overlay box display settings",
		position = 8
    )
    String boxSection = "boxSection";
	
	@ConfigItem(
		keyName = "boxOffset",
		name = "Offset",
		description = "Pixel offsets of the overlay box",
		section = boxSection,
		position = 0
	)
	default Dimension boxOffset()
	{
		return new Dimension(0, 0);
	}

	@ConfigItem(
		keyName = "boxPadding",
		name = "Padding",
		description = "Pixel padding of the overlay box",
		section = boxSection,
		position = 1
	)
	default Dimension boxPadding()
	{
		return new Dimension(1, 1);
	}

	@ConfigItem(
		keyName = "showBoxFill",
		name = "Show fill",
		description = "Whether to show the fill of the box on the overlay",
		section = boxSection,
		position = 4
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
		position = 5
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
		position = 6
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
		position = 7
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
		position = 8
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
		position = 9
	)
	default Color unprofitableColor()
	{
		return Color.RED;
	}
	
	//#region Number text section
	@ConfigSection(
        name = "Overlay - Profitable Amount",
        description = "Profitable amount text display settings", 
		position = 9
    )
    String numberSection = "numberSection";

	@ConfigItem(
		keyName = "showNumber",
		name = "Show",
		description = "Whether to show the number of items that can be sold profitably on the overlay",
		section = numberSection,
		position = 0
	)
	default boolean showNumber()
	{
		return true;
	}

	@ConfigItem(
		keyName = "numberOffset",
		name = "Offset",
		description = "Pixel offset of the number text overlay",
		section = numberSection,
		position = 1
	)
	default Dimension numberOffset()
	{
		return new Dimension(2, 34);
	}
	
	@ConfigItem(
		keyName = "numberColor",
		name = "Color",
		description = "Color used for number text overlay",
		section = numberSection,
		position = 2
	)
	default Color numberColor()
	{
		return Color.WHITE;
	}
	
	//#region Profit text section
	@ConfigSection(
        name = "Overlay - Total Profit",
        description = "Total potential profit text display settings",
		position = 10
    )
    String profitSection = "profitSection";

	@ConfigItem(
		keyName = "showProfit",
		name = "Show",
		description = "Whether to show the total potential profit on the overlay",
		section = profitSection,
		position = 0
	)
	default boolean showProfit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "profitOffset",
		name = "Offset",
		description = "Pixel offset of the profit text overlay",
		section = profitSection,
		position = 1
	)
	default Dimension profitOffset()
	{
		return new Dimension(38, 34);
	}

	@ConfigItem(
		keyName = "profitColor",
		name = "Color",
		description = "Color used for profit text overlay",
		section = profitSection,
		position = 2
	)
	default Color profitColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "shortenProfit",
		name = "Abbreviate",
		description = "Whether to abbreviate the profit text when appropriate, eg. 54321 becomes 54.3k",
		section = profitSection,
		position = 3
	)
	default boolean shortenProfit()
	{
		return true;
	}

	//#region Current Price section
	@ConfigSection(
        name = "Overlay - Current Price",
        description = "Current price overlay display settings",
		position = 11
    )
    String currentPriceSection = "currentPriceSection";

	@ConfigItem(
		keyName = "showCurrentPrice",
		name = "Show",
		description = "Whether to show the current price on the overlay",
		section = currentPriceSection,
		position = 0
	)
	default boolean showCurrentPrice()
	{
		return false;
	}

	@ConfigItem(
		keyName = "currentPriceOffset",
		name = "Offset",
		description = "Pixel offset of the current price text overlay",
		section = currentPriceSection,
		position = 1
	)
	default Dimension currentPriceOffset()
	{
		return new Dimension(38, 24);
	}

	@ConfigItem(
		keyName = "currentPriceColor",
		name = "Color",
		description = "Color used for current price text overlay",
		section = currentPriceSection,
		position = 2
	)
	default Color currentPriceColor()
	{
		return Color.YELLOW;
	}
	
	@ConfigItem(
		keyName = "shortenCurrentPrice",
		name = "Abbreviate",
		description = "Whether to abbreviate the current price text when appropriate, eg. 54321 becomes 54.3k",
		section = currentPriceSection,
		position = 3
	)
	default boolean shortenCurrentPrice()
	{
		return true;
	}

	//#region Internal
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

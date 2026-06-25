package com.hagglehelper;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.collect.ImmutableSet;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;

@Slf4j
public class Shop {
    @Inject
    private HaggleHelperConfig config;
    
    public static final Set<String> MENU_OPTIONS = ImmutableSet.of("Sell 50", "Sell 10", "Sell 5", "Sell 1");

    String name;
    int sellsAt;
    int buysAt;
    float changePer;
    Map<Integer, Integer> defaultStocks;
    Map<Integer, Integer> currentStocks;
    boolean isGeneral;

    public void updateStock(Item[] items) {
		log.debug("Updating items={} on shop={}", items, this);
        currentStocks = Arrays.stream(items).collect(Collectors.toMap(Item::getId, Item::getQuantity));
    }

    public int getStockDelta(int itemId)
    {
        int defaultStock = defaultStocks.getOrDefault(itemId, 0);
        int currentStock = currentStocks.getOrDefault(itemId, 0);
        return defaultStock - currentStock;
    }

    public int getStockDelta(HighlightedItem item) 
    {   
        return getStockDelta(item.id);
    }
    
    private int getItemPrice(int itemId, int itemValue, int multiplier) 
    { 
        int delta = getStockDelta(itemId);
        return (int) Math.floor(itemValue * (multiplier + delta*changePer) / 100);
    }

    public int getItemPriceSellTo(HighlightedItem item) 
    {
        return getItemPriceSellTo(item.id, item.value);
    }

    public int getItemPriceSellTo(int itemId, int itemValue) 
    {
        return getItemPrice(itemId, itemValue, buysAt);
    }

    public int getItemPriceBuyFrom(HighlightedItem item) 
    {
        return getItemPriceBuyFrom(item.id, item.value);
    }

    public int getItemPriceBuyFrom(int itemId, int itemValue) 
    {
        return getItemPrice(itemId, itemValue, sellsAt);
    }

    public int getNumProfitableSellTo(int itemId, int cost, int itemValue) 
    {
        float percent = 100f * (cost + config.profitThreshold()) / itemValue;
        float num = (buysAt - percent) / changePer;
        return Math.max(0, (int) Math.ceil(num) + getStockDelta(itemId));
    }

    public int getNumProfitableSellTo(HighlightedItem item) 
    {
        return getNumProfitableSellTo(item.id, item.cost, item.value);
    }

    public int getNumProfitableBuyFrom(int itemId, int cost, int itemValue) 
    {
        float percent = 100f * (cost - config.profitThreshold()) / itemValue;
        float num = (percent - sellsAt) / changePer;
        int adjustedNum = Math.max(0, (int) Math.ceil(num) - getStockDelta(itemId));
        return Math.min(currentStocks.get(itemId), adjustedNum);
    }

    public int getNumProfitableBuyFrom(HighlightedItem item) 
    {
        return getNumProfitableBuyFrom(item.id, item.cost, item.value);
    }

    public int getTotalProfitSellTo(int itemId, int cost, int itemValue) 
    {
        int num = getNumProfitableSellTo(itemId, cost, itemValue);
        return getProfitSellTo(num, itemId, cost, itemValue);
    }

    public int getTotalProfitSellTo(HighlightedItem item) 
    {
        return getTotalProfitSellTo(item.id, item.cost, item.value);
    }

    public int getTotalProfitBuyFrom(int itemId, int cost, int itemValue) 
    {
        int num = getNumProfitableBuyFrom(itemId, cost, itemValue);
        return getProfitBuyFrom(num, itemId, cost, itemValue);
    }

    public int getTotalProfitBuyFrom(HighlightedItem item) 
    {
        return getTotalProfitBuyFrom(item.id, item.cost, item.value);
    }
    
    public int getRevenueBuyFrom(int quantity, HighlightedItem item) 
    {
        return getRevenueBuyFrom(quantity, item.id, item.value);
    }

    public int getRevenueBuyFrom(int quantity, int itemId, int itemValue) 
    {
        if (quantity <= 0) 
        {
            return 0;
        }

        float pricePercent = sellsAt + changePer * getStockDelta(itemId);
        int revenue = 0;
        for (int i = 0; i < quantity; i++, pricePercent += changePer) {
            revenue += (int) Math.floor(pricePercent * itemValue / 100);
        }

        return revenue;
    }

    public int getRevenueSellTo(int quantity, HighlightedItem item) 
    {
        return getRevenueSellTo(quantity, item.id, item.value);
    }

    private int getRevenueSellTo(int quantity, int itemId, int itemValue) 
    {
        if (quantity <= 0) 
        {
            return 0;
        }

        float pricePercent = buysAt + changePer * getStockDelta(itemId);
        int revenue = 0;
        for (int i = 0; i < quantity; i++, pricePercent -= changePer) {
            revenue += (int) Math.floor(pricePercent * itemValue / 100);
        }

        return revenue;
    }

    public int getProfitSellTo(int quantity, HighlightedItem item) 
    {
        return getProfitSellTo(quantity, item.id, item.cost, item.value);
    }

    public int getProfitSellTo(int quantity, int itemId, int cost, int itemValue) 
    {
        return quantity > 0 
            ? getRevenueSellTo(quantity, itemId, itemValue) - cost * quantity
            : 0;
    }
    
    public int getProfitBuyFrom(int quantity, HighlightedItem item) 
    {
        return getProfitBuyFrom(quantity, item.id, item.cost, item.value);
    }

    public int getProfitBuyFrom(int quantity, int itemId, int cost, int itemValue) 
    {
        return quantity > 0 
            ? cost * quantity - getRevenueBuyFrom(quantity, itemId, itemValue)
            : 0;
    }

    public Color getColorSellTo(HighlightedItem item)
    {
        return getColor(
            getProfitSellTo(1, item),
            getProfitSellTo(5, item),
            getProfitSellTo(10, item),
            getNumProfitableSellTo(item),
            getTotalProfitSellTo(item)
        );
    }

    public Color getColorBuyFrom(HighlightedItem item) 
    {
        return getColor(
            getProfitBuyFrom(1, item),
            getProfitBuyFrom(5, item),
            getProfitBuyFrom(10, item),
            getNumProfitableBuyFrom(item),
            getTotalProfitBuyFrom(item)
        );
    }

    private Color getColor(int profit1, int profit5, int profit10, int numProfitable, int maxProfit)
    {
        int profitThreshold = config.profitThreshold();
        int bulkLossAllowance = config.bulkLossAllowance();

        if (maxProfit <= profitThreshold)
        {
            return config.unprofitableColor();
        }
        else if (profit10 > 10*profitThreshold && profit10 > Math.max(profit5, profit1) && (numProfitable >= 10 || maxProfit - profit10 < bulkLossAllowance))
        {
            return config.tenProfitableColor();
        }
        else if (profit5 > 5*profitThreshold && profit5 > profit1 && (numProfitable >= 5 || maxProfit - profit5 < bulkLossAllowance))
        {
            return config.fiveProfitableColor();
        }
        else
        {
            return config.oneProfitableColor();
        }
    }

    public boolean isTradableWith(int itemId) {
        return isGeneral || defaultStocks.containsKey(itemId) || currentStocks.containsKey(itemId);
    }

    public boolean isTradableWith(HighlightedItem item) {
        return isTradableWith(item.id);
    }

    @Override
    public String toString() {
        return String.format(
            "Shop{name=%s, sellsAt=%d, buysAt=%d, changePer=%f, defaultStocks=%s, currentStocks=%s, general=%s}", 
            name, sellsAt, buysAt, changePer, defaultStocks, currentStocks, isGeneral
        );
    }
}

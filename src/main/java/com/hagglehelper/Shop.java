package com.hagglehelper;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public class Shop {
    public static final Set<String> MENU_OPTIONS = ImmutableSet.of("Sell 50", "Sell 10", "Sell 5", "Sell 1");

    int sellsAt;
    int buysAt;
    float changePer;
    Map<Integer, Integer> stocks;
    boolean general;

    public int getStockDelta(int itemId, int currentStock) 
    {
        int defaultStock = stocks.getOrDefault(itemId, 0);
        return defaultStock - currentStock;
    }
    
    private int getItemPrice(int itemId, int currentStock, int itemValue, int multiplier) 
    { 
        int delta = getStockDelta(itemId, currentStock);
        return (int) Math.floor(itemValue * (multiplier + delta*changePer) / 100);
    }

    public int getItemBuyPrice(int itemId, int currentStock, int itemValue) 
    {
        return getItemPrice(itemId, currentStock, itemValue, buysAt);
    }

    public int getItemSellPrice(int itemId, int currentStock, int itemValue) 
    {
        return getItemPrice(itemId, currentStock, itemValue, sellsAt);
    }

    public int getNumProfitable(int itemId, int currentStock, int costPrice, int itemValue, int profitThreshold) 
    {
        float percent = 100f * (costPrice + profitThreshold) / itemValue;
        float num = (buysAt - percent) / changePer;
        return Math.max(0, (int) Math.ceil(num) + getStockDelta(itemId, currentStock));
    }

    public int getTotalProfit(int itemId, int currentStock, int costPrice, int itemValue, int profitThreshold) 
    {
        int num = getNumProfitable(itemId, currentStock, costPrice, itemValue, profitThreshold);
        return getProfit(num, itemId, currentStock, costPrice, itemValue);
    }

    public int getRevenue(int quantity, int itemId, int currentStock, int itemValue) 
    {
        if (quantity <= 0) 
        {
            return 0;
        }

        float pricePercent = buysAt + changePer * getStockDelta(itemId, currentStock);
        int revenue = 0;
        for (int i = 0; i < quantity; i++, pricePercent -= changePer) {
            revenue += (int) Math.floor(pricePercent * itemValue / 100);
        }

        return revenue;
    }

    public int getProfit(int quantity, int itemId, int currentStock, int costPrice, int itemValue) 
    {
        if (quantity <= 0) 
        {
            return 0;
        }

        int revenue = getRevenue(quantity, itemId, currentStock, itemValue);
        return revenue - costPrice * quantity;
    }
    
    public int getProfitDelta(int quantity, int itemId, int currentStock, int costPrice, int itemValue, int profitThreshold)
    {
        int profit = getProfit(quantity, itemId, currentStock, costPrice, itemValue);
        int totalProfit = getTotalProfit(itemId, currentStock, costPrice, itemValue, profitThreshold);
        return totalProfit - profit;
    }

    @Override
    public String toString() {
        return String.format(
            "Shop{sellsAt=%d, buysAt=%d, changePer=%f, stocks=%s, general=%s}", 
            sellsAt, buysAt, changePer, stocks, general
        );
    }
}

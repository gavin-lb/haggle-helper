package com.hagglehelper;

import java.util.HashMap;
import java.util.Map;

public class HighlightedItem 
{
    public int itemId;
    public int numProfitable;
    public int maxProfit;
    public int itemValue;
    public int currentStock;
    public int costPrice;
    public int currentBuyPrice;
    public int currentSellPrice;
    public HashMap<Integer, Integer> profits;
    public HashMap<Integer, Integer> revenues;
    public Shop shop;

    public HighlightedItem(int itemId) 
    {
        this.itemId = itemId;
        this.profits = new HashMap<>(Map.of(
            1, 0, 
            5, 0, 
            10, 0
        ));
        this.revenues = new HashMap<>(Map.of(
            1, 0, 
            5, 0, 
            10, 0
        ));
    }

    @Override
    public String toString()
    {
        return String.format(
            "HighlightedItem{itemId=%s, numProfitable=%s, totalProfit=%s, itemValue=%s, currentStock=%s, costPrice=%s, profits=%s, revenues=%s, shop=%s}", 
            this.itemId, this.numProfitable, this.maxProfit, this.itemValue, this.currentStock, this.costPrice, this.profits, this.revenues, this.shop
        );
    }
}
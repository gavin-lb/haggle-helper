package com.hagglehelper;

import java.awt.Color;

public class HighlightedItem 
{
    public int id;
    public int value;
    public int cost;
    
    public int numProfitable;
    public int maxProfit;
    public int currentPrice;
    public Color color;

    public HighlightedItem(TrackedItem item) 
    {
        this.id = item.getItemId();
        this.value = item.getValue();
        this.cost = item.getCost();
    }
    public HighlightedItem(int id, int value, int cost) 
    {
        this.id = id;
        this.value = value;
        this.cost = cost;
    }

    public void sellTo(Shop shop) 
    {   
        this.numProfitable = shop.getNumProfitableSellTo(this);
        this.maxProfit = shop.getTotalProfitSellTo(this);
        this.currentPrice = shop.getItemPriceSellTo(this);
        this.color = shop.getColorSellTo(this);
    }
    
    public void buyFrom(Shop shop) 
    {   
        this.numProfitable = shop.getNumProfitableBuyFrom(this);
        this.maxProfit = shop.getTotalProfitBuyFrom(this);
        this.currentPrice = shop.getItemPriceBuyFrom(this);
        this.color = shop.getColorBuyFrom(this);
    }

    @Override
    public String toString()
    {
        return String.format(
            "HighlightedItem{id=%d, value=%d, cost=%d, numProfitable=%d, maxProfit=%d, currentPrice=%d}", 
            this.id, this.value, this.cost, this.numProfitable, this.maxProfit, this.currentPrice
        );
    }
}
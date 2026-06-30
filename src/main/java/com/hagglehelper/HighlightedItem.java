package com.hagglehelper;

import com.hagglehelper.HaggleHelperConfig.InterfaceMode;

import lombok.ToString;

import java.awt.Color;

@ToString
public class HighlightedItem
{
	final public int id;
	final public int value;
	final public int cost;

	public int numProfitable;
	public int maxProfit;
	public int currentPrice;
	public Color color;
	public InterfaceMode mode;

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
		this.mode = InterfaceMode.INVENTORY;
	}

	public void buyFrom(Shop shop)
	{
		this.numProfitable = shop.getNumProfitableBuyFrom(this);
		this.maxProfit = shop.getTotalProfitBuyFrom(this);
		this.currentPrice = shop.getItemPriceBuyFrom(this);
		this.color = shop.getColorBuyFrom(this);
		this.mode = InterfaceMode.SHOP;
	}
}
package com.hagglehelper;

import com.google.common.collect.ImmutableMap;
import com.hagglehelper.HaggleHelperConfig.InterfaceMode;
import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;

@Slf4j
@ToString
public class Shop
{
	private static final int SELL_TO_FLOOR = 10;
	private static final Map<Integer, Integer> FIXED_BUY_FROM_PRICE = ImmutableMap
		.<Integer, Integer>builder()
		.put(981, 21000)
		.put(21354, 1)
		.put(11705, 75)
		.put(11706, 75)
		.build();

	@ToString.Exclude
	@Inject
	private HaggleHelperConfig config;

	@ToString.Exclude
	private boolean lumbridgeElite;

	String name;
	int containerId;
	int sellsAt;
	int buysAt;
	float changePer;
	Map<Integer, Integer> defaultStocks;
	private Map<Integer, Integer> currentStocks;
	boolean isGeneral;
	final Map<Integer, Integer> queue = new HashMap<>();

	private int applyDiaryDiscount(int price)
	{
		if (lumbridgeElite && name.contains("Culinaromancer"))
		{
			price -= price * 20 / 100;
		}
		return price;
	}

	public void setLumbridgeElite(boolean value)
	{
		lumbridgeElite = value;
	}

	public int getStock(int itemId)
	{
		return currentStocks != null
			? currentStocks.getOrDefault(itemId, 0) + queue.getOrDefault(itemId, 0)
			: 0;
	}

	public int getStock(HighlightedItem item)
	{
		return getStock(item.id);
	}

	@SuppressWarnings("null")
	public boolean updateStock(Item[] items, int containerId)
	{
		this.containerId = containerId;
		log.debug("Updating items={} on shop={}", items, this);
		Map<Integer, Integer> newStocks = Arrays.stream(items)
			.filter(item -> item.getId() > 0)
			.collect(Collectors.toMap(Item::getId, Item::getQuantity));

		if (newStocks.equals(currentStocks))
		{
			log.debug("Stock unchanged");
			return false;
		}

		if (currentStocks != null && !queue.isEmpty())
		{
			for (Item item : items)
			{
				int itemId = item.getId();
				int queued = queue.getOrDefault(itemId, 0);
				if (queued != 0)
				{
					int previousStock = currentStocks.getOrDefault(itemId, 0);
					int newStock = newStocks.get(itemId);
					log.debug("queued item, queued={} previousStock={} currentStock={}", queued,
						previousStock, newStock);

					int newQueued = queued - newStock + previousStock;
					if (newQueued != 0)
					{
						queue.put(itemId, newQueued);
						log.debug("newQueued={}", newQueued);
					}
					else
					{
						queue.remove(itemId);
						log.debug("removed queued");
					}
				}
			}
		}

		currentStocks = newStocks;
		return true;
	}

	public int getStockDelta(int itemId)
	{
		int defaultStock = defaultStocks.getOrDefault(itemId, 0);
		int currentStock = getStock(itemId);
		return defaultStock - currentStock;
	}

	public int getStockDelta(HighlightedItem item)
	{
		return getStockDelta(item.id);
	}

	private int getItemPrice(int itemId, int itemValue, int multiplier)
	{
		int delta = getStockDelta(itemId);
		return (int) Math.floor(itemValue * (multiplier + delta * changePer) / 100);
	}

	public int getItemPrice(HighlightedItem item)
	{
		return item.mode == InterfaceMode.INVENTORY
			? getItemPriceSellTo(item.id, item.value)
			: getItemPriceBuyFrom(item.id, item.value);
	}

	public int getItemPriceSellTo(HighlightedItem item)
	{
		return getItemPriceSellTo(item.id, item.value);
	}

	public int getItemPriceSellTo(int itemId, int itemValue)
	{
		return Math.max(getItemPrice(itemId, itemValue, buysAt), SELL_TO_FLOOR * itemValue / 100);
	}

	public int getItemPriceBuyFrom(HighlightedItem item)
	{
		return getItemPriceBuyFrom(item.id, item.value);
	}

	public int getItemPriceBuyFrom(int itemId, int itemValue)
	{
		if (FIXED_BUY_FROM_PRICE.containsKey(itemId))
		{
			return FIXED_BUY_FROM_PRICE.get(itemId);
		}
		return Math.max(1, applyDiaryDiscount(
			Math.max(getItemPrice(itemId, itemValue, sellsAt), (sellsAt - 100) * itemValue / 100)
		));
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
		if (FIXED_BUY_FROM_PRICE.containsKey(itemId))
		{
			return 0;
		}

		int lumbModifier = lumbridgeElite && name.contains("Culinaromancer")
			? 20
			: 0;

		float percent = 100f * (cost - config.profitThreshold()) / itemValue;
		float num = (percent - sellsAt + lumbModifier) / changePer;
		int adjustedNum = Math.max(0, (int) Math.ceil(num) - getStockDelta(itemId));
		return Math.min(getStock(itemId), adjustedNum);
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
		for (int i = 0; i < quantity; i++, pricePercent += changePer)
		{
			revenue += FIXED_BUY_FROM_PRICE.containsKey(itemId)
				? FIXED_BUY_FROM_PRICE.get(itemId)
				: Math.max(1, applyDiaryDiscount(
					(int) Math.floor(Math.max(pricePercent, sellsAt - 100) * itemValue / 100)
				));
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
		for (int i = 0; i < quantity; i++, pricePercent -= changePer)
		{
			revenue += (int) Math.floor(Math.max(pricePercent, SELL_TO_FLOOR) * itemValue / 100);
		}

		return revenue;
	}

	public int getProfitSellTo(int quantity, HighlightedItem item)
	{
		return getProfitSellTo(quantity, item.id, item.cost, item.value);
	}

	public int getProfitSellTo(int quantity, int itemId, int cost, int itemValue)
	{
		return quantity > 0 ? getRevenueSellTo(quantity, itemId, itemValue) - cost * quantity : 0;
	}

	public int getProfitBuyFrom(int quantity, HighlightedItem item)
	{
		return getProfitBuyFrom(quantity, item.id, item.cost, item.value);
	}

	public int getProfitBuyFrom(int quantity, int itemId, int cost, int itemValue)
	{
		return quantity > 0 ? cost * quantity - getRevenueBuyFrom(quantity, itemId, itemValue) : 0;
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
		else if (profit10 > 10 * profitThreshold && profit10 > Math.max(profit5, profit1) &&
			(numProfitable >= 10 || maxProfit - profit10 < bulkLossAllowance))
		{
			return config.tenProfitableColor();
		}
		else if (profit5 > 5 * profitThreshold && profit5 > profit1 && (numProfitable >= 5 ||
			maxProfit - profit5 < bulkLossAllowance))
		{
			return config.fiveProfitableColor();
		}
		else
		{
			return config.oneProfitableColor();
		}
	}

	public boolean isTradableWith(int itemId)
	{
		return isGeneral || defaultStocks.containsKey(itemId) || currentStocks.containsKey(itemId);
	}

	public boolean isTradableWith(HighlightedItem item)
	{
		return isTradableWith(item.id);
	}

	public Integer processTransaction(String menuOption, HighlightedItem item)
		throws UnprofitableTransactionException
	{
		if (!isTradableWith(item))
		{
			return null;
		}

		final int amount = Integer.parseInt(menuOption.replaceAll("\\D", ""));
		int profit = item.mode == InterfaceMode.INVENTORY
			? getProfitSellTo(amount, item)
			: getProfitBuyFrom(amount, item);
		int profitDelta = item.maxProfit - profit;

		int queued = queue.getOrDefault(item.id, 0);
		if (queued != 0)
		{
			log.debug("Transaction while queued, queued={} amount={} item={}", queued, amount,
				item);
		}

		if (profit > amount * config.profitThreshold() && (amount <= item.numProfitable ||
			profitDelta <= config.bulkLossAllowance()))
		{
			log.debug(
				"Allowing profitable transaction: amount={} queued={} profit={} profitDelta={} item={}",
				amount, queued, profit, profitDelta, item);
			queue.put(item.id, queued + (item.mode == InterfaceMode.INVENTORY ? amount : -amount));
			item.numProfitable -= amount;
			item.maxProfit -= profit;
			item.currentPrice = getItemPrice(item);
			return profit;
		}

		log.debug("Blocked transaction: amount={} queued={} profit={} profitDelta={} item={}",
			amount, queued, profit, profitDelta, item);
		throw new UnprofitableTransactionException();
	}

	public static class UnprofitableTransactionException extends Exception
	{
	}
}

package com.hagglehelper;

import com.google.inject.Inject;
import com.hagglehelper.HaggleHelperConfig.InterfaceMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class HighlightedItemsManager
{
	@Inject
	private HaggleHelperPlugin plugin;

	@Inject
	private TrackedItemsManager trackedItemsManager;

	@Inject
	private ItemManager itemManager;

	private final Map<InterfaceMode, Map<Integer, HighlightedItem>> items = new EnumMap<>(InterfaceMode.class)
	{
		{
			put(InterfaceMode.INVENTORY, new HashMap<>());
			put(InterfaceMode.SHOP, new HashMap<>());
		}
	};

	private HighlightedItem buildItem(int itemId)
	{
		return trackedItemsManager.isTrackedItemId(itemId) ? new HighlightedItem(trackedItemsManager.getTrackedItem(itemId)) : new HighlightedItem(
			itemId, itemManager.getItemComposition(itemId).getPrice(), itemManager.getItemPrice(itemId)
		);
	}

	private HighlightedItem createItem(int itemId, InterfaceMode mode)
	{
		HighlightedItem item = buildItem(itemId);

		switch (mode)
		{
			case INVENTORY:
				item.sellTo(plugin.shop);
				break;

			case SHOP:
				item.buyFrom(plugin.shop);
				break;

			default:
				throw new IllegalArgumentException("Unsupported interface mode");
		}

		log.debug("Created new {}-mode item {}", mode, item);
		return item;
	}

	public HighlightedItem getOrCreate(int itemId, InterfaceMode mode)
	{
		return items.get(mode).computeIfAbsent(
			trackedItemsManager.getUnnotedId(itemId), id -> createItem(id, mode)
		);
	}

	@SuppressWarnings("null")
	public void clear()
	{
		items.values().forEach(Map::clear);
	}
}

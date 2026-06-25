package com.hagglehelper;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.hagglehelper.HaggleHelperConfig.DisplayMode;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class HighlightedItemsManager {
    @Inject
    private HaggleHelperPlugin plugin;

    @Inject
    private TrackedItemsManager trackedItemsManager;

    @Inject
    private ItemManager itemManager;

    private final Map<Integer, HighlightedItem> inventoryItems = new HashMap<>();
    private final Map<Integer, HighlightedItem> shopItems = new HashMap<>();
    
    private HighlightedItem buildItem(int itemId)
    {
        return trackedItemsManager.isTrackedItemId(itemId) 
            ? new HighlightedItem(trackedItemsManager.getTrackedItem(itemId))
            : new HighlightedItem(
                itemId, 
                itemManager.getItemComposition(itemId).getPrice(), 
                itemManager.getItemPrice(itemId)
            );
    }

    private HighlightedItem createShopItem(Integer itemId)
    {
        HighlightedItem item = buildItem(itemId);

        item.buyFrom(plugin.shop);
        log.debug("Created new shop {}", item);

        return item;
    }

    private HighlightedItem createInventoryItem(Integer itemId)
    {
        HighlightedItem item = buildItem(itemId);

        item.sellTo(plugin.shop);
        log.debug("Created new inventory {}", item);

        return item;
    }

    public HighlightedItem getOrCreate(int itemId, DisplayMode mode)
    {
        itemId = trackedItemsManager.getUnnotedId(itemId);
        return mode == DisplayMode.INVENTORY 
            ? inventoryItems.computeIfAbsent(itemId, this::createInventoryItem)
            : shopItems.computeIfAbsent(itemId, this::createShopItem);
    }

    public void clear() 
    {
        inventoryItems.clear();
        shopItems.clear();
    }
}

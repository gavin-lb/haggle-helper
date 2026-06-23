package com.hagglehelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
 
import javax.inject.Inject;
import javax.inject.Singleton;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
 
@Slf4j
@Singleton
public class TrackedItemsManager
{
    @Inject
    private HaggleHelperConfig config;
 
    @Inject
    private Gson gson;
 
    private final Map<Integer, Integer> costs = new HashMap<>();
    private final Map<Integer, String> names = new LinkedHashMap<>();
 
    @Inject
    public void init()
    {
        reloadFromConfig();
    }
 
    public int getCost(int itemId)
    {
        return costs.getOrDefault(itemId, -1);
    }
 
    public void setCost(int itemId, String itemName, int cost)
    {
        costs.put(itemId, cost);
        names.put(itemId, itemName);
        persist();
    }
 
    public void removeItem(int itemId)
    {
        costs.remove(itemId);
        names.remove(itemId);
        persist();
    }
 
    public Set<Integer> getTrackedItemIds()
    {
        return Collections.unmodifiableSet(costs.keySet());
    }
 
    public List<TrackedItem> getTrackedItems()
    {
        List<TrackedItem> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : costs.entrySet())
        {
            int id = e.getKey();
            list.add(new TrackedItem(id, names.getOrDefault(id, "Item " + id), e.getValue()));
        }
        list.sort(Comparator.comparing(TrackedItem::getName));
        return list;
    }
 
    public void reloadFromConfig()
    {
        costs.clear();
        names.clear();
        try
        {
            String json = config.itemCostsJson();
            if (json != null && !json.isBlank() && !json.equals("{}"))
            {
                // Stored format: { "id": { "name": "...", "cost": 123 }, ... }
                Type fullType = new TypeToken<Map<Integer, StoredEntry>>() {}.getType();
                Map<Integer, StoredEntry> stored = gson.fromJson(json, fullType);
                if (stored != null)
                {
                    for (Map.Entry<Integer, StoredEntry> e : stored.entrySet())
                    {
                        costs.put(e.getKey(), e.getValue().cost);
                        names.put(e.getKey(), e.getValue().name);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("Failed to load item costs from config", ex);
        }
    }
 
    private void persist()
    {
        try
        {
            Map<Integer, StoredEntry> stored = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> e : costs.entrySet())
            {
                stored.put(
                    e.getKey(),
                    new StoredEntry(names.getOrDefault(e.getKey(), "Item " + e.getKey()), e.getValue())
                );
            }
            config.setItemCostsJson(gson.toJson(stored));
        }
        catch (Exception ex)
        {
            log.warn("Failed to save item costs to config", ex);
        }
    }

    private static class StoredEntry
    {
        String name;
        int cost;
 
        StoredEntry(String name, int cost)
        {
            this.name = name;
            this.cost = cost;
        }
    }
 
    public static class TrackedItem
    {
        private final int itemId;
        private final String name;
        private final int cost;
 
        public TrackedItem(int itemId, String name, int cost)
        {
            this.itemId = itemId;
            this.name = name;
            this.cost = cost;
        }
 
        public int getItemId() { return itemId; }
        public String getName() { return name; }
        public int getCost() { return cost; }
    }
}
 

package com.hagglehelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class TrackedItemsManager
{
	@Inject
	private HaggleHelperConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	private final Map<Integer, Integer> costs = new HashMap<>();
	private final Map<Integer, String> names = new LinkedHashMap<>();

	private final LoadingCache<Integer, Integer> unnotedIds = CacheBuilder.newBuilder()
		.maximumSize(10_000)
		.build(new CacheLoader<>()
		{
			@Override
			public Integer load(@Nonnull Integer itemId)
			{
				ItemComposition item = itemManager.getItemComposition(itemId);
				return item.getNote() == -1 ? itemId : item.getLinkedNoteId();
			}
		});

	public int getUnnotedId(int itemId)
	{
		return unnotedIds.getUnchecked(itemId);
	}

	@Inject
	public void init()
	{
		reloadFromConfig();
	}

	public int getCost(int itemId)
	{
		itemId = getUnnotedId(itemId);
		return costs.get(itemId);
	}

	public void setCost(int itemId, String itemName, int cost)
	{
		itemId = getUnnotedId(itemId);
		costs.put(itemId, cost);
		names.put(itemId, itemName);
		persist();
	}

	public void removeAllItems()
	{
		costs.clear();
		names.clear();
		persist();
	}

	public void removeItem(int itemId)
	{
		itemId = getUnnotedId(itemId);
		costs.remove(itemId);
		names.remove(itemId);
		persist();
	}

	public Set<Integer> getTrackedItemIds()
	{
		return Collections.unmodifiableSet(costs.keySet());
	}

	public boolean isTrackedItemId(int itemId)
	{
		return costs.containsKey(getUnnotedId(itemId));
	}

	public TrackedItem[] getTrackedItems()
	{
		return costs.keySet().stream()
			.map(id -> new TrackedItem(id, names.get(id), costs.get(id), itemManager
				.getItemComposition(id).getPrice()))
			.sorted(Comparator.comparing(item -> item.getName()))
			.toArray(TrackedItem[]::new);
	}

	public TrackedItem getTrackedItem(int itemId)
	{
		itemId = getUnnotedId(itemId);

		return isTrackedItemId(itemId)
			? new TrackedItem(
				itemId,
				names.get(itemId),
				costs.get(itemId),
				itemManager.getItemComposition(itemId).getPrice()
			)
			: null;
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
				Type fullType = new TypeToken<Map<Integer, StoredEntry>>()
				{
				}.getType();
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
					new StoredEntry(names.getOrDefault(e.getKey(), "Item " + e.getKey()), e
						.getValue())
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
		final String name;
		final int cost;

		StoredEntry(String name, int cost)
		{
			this.name = name;
			this.cost = cost;
		}
	}
}


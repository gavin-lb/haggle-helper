package com.hagglehelper;

import com.google.inject.Provides;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Type;
import java.awt.image.BufferedImage;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Haggle Helper"
)
public class HaggleHelperPlugin extends Plugin
{
	@Inject
	private Client client;
	
	@Inject
	private ClientThread clientThread;

	@Inject
	private HaggleHelperConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
    private TrackedItemsManager trackedItemsManager;

	@Inject
    private OverlayManager overlayManager;

    @Inject
    private HaggleHelperOverlay inventoryOverlay;

    @Inject
    private HaggleHelperOverlayPanel overlayPanel;

	@Inject
	private Gson gson;

	private HaggleHelperPanel panel;
	private NavigationButton navButton;

	private static final String SHOPS_RESOURCE = "shops.json";
	private static final Type SHOP_TYPE = new TypeToken<Map<String, Shop>>(){}.getType();
	private static final int INVENTORY_CONTAINER_ID = 93;
	
	private final Map<Integer, Integer> queuedSales = new HashMap<>();
	
	public Map<Integer, HighlightedItem> highlightedItems = new HashMap<>();
	public Map<String, Shop> shopsMap = new HashMap<>();
	

	public static String VERSION;

	@Override
	protected void startUp() throws Exception
	{
		VERSION = getVersion();
		
		InputStream stream = getClass().getClassLoader().getResourceAsStream(SHOPS_RESOURCE);
        if (stream == null) {
            throw new IllegalArgumentException("Resource not found.");
        }
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            this.shopsMap = gson.fromJson(reader, SHOP_TYPE);
        } catch (IOException e) {
            log.error("Failed to read JSON file \"{}\": {}", SHOPS_RESOURCE, e.getMessage());
        }

		panel = injector.getInstance(HaggleHelperPanel.class);
		panel.init();
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "hagglehelper_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Haggle Helper")
			.icon(icon)
			.priority(11)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(inventoryOverlay);
		overlayManager.add(overlayPanel);

		clientThread.invoke(this::rebuildHighlights);

		log.debug("Haggle Helper started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		panel.deinit();
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;

		overlayManager.remove(inventoryOverlay);
		overlayManager.remove(overlayPanel);

		highlightedItems.clear();

		log.debug("Haggle Helper stopped!");
	}

	private void getStock(Item[] shopItems)
	{
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
        if (frame == null || frame.isHidden())
		{
			return;
		}

		String shopName = frame.getDynamicChildren()[1].getText();
		Shop shop = shopsMap.get(shopName);
		log.debug("Getting stock of shop name={}, shop={}", shopName, shop);
		Map<Integer, Integer> shopStockMap = Arrays.stream(shopItems).collect(Collectors.toMap(Item::getId, Item::getQuantity));

		Set<Integer> trackedItems = trackedItemsManager.getTrackedItemIds();
		for (int itemId : trackedItems)
        {
			if (!shop.general && !shopStockMap.containsKey(itemId)) {
				continue;
			}

			int shopStock = shopStockMap.getOrDefault(itemId, 0);
			int itemValue = client.getItemDefinition(itemId).getPrice();
			int buyPrice = shop.getItemBuyPrice(itemId, shopStock, itemValue); 
			int sellPrice = shop.getItemSellPrice(itemId, shopStock, itemValue); 
			int costPrice = trackedItemsManager.getCost(itemId);
			int numProfitable = shop.getNumProfitable(itemId, shopStock, costPrice, itemValue, config.profitThreshold());
			int profit = shop.getTotalProfit(itemId, shopStock, costPrice, itemValue, config.profitThreshold());
            log.debug(
				"itemId={} itemQuantity={} buyPrice={} sellPrice={} costPrice={} numProfitable={} profit={}", 
				itemId, shopStock, buyPrice, sellPrice, costPrice, numProfitable, profit
			);

			HighlightedItem highlightedItem = highlightedItems.get(itemId);
			if (highlightedItem == null)
			{
				continue;
			}

			Integer queued = queuedSales.get(itemId);
			if (queued != null)
			{
				int previousStock = highlightedItem.currentStock;
				log.debug("queued item, queued={} previousStock={} currentStock={}", queuedSales.get(itemId), previousStock, shopStock);
				
				int newQueued = queued - shopStock + previousStock;
				if (newQueued != 0)
				{
					queuedSales.put(itemId, newQueued);
					log.debug("newQueued={}", newQueued);
				}
				else
				{
					queuedSales.remove(itemId);
					log.debug("removed queued");
				}
			}

			highlightedItem.numProfitable = numProfitable;
			highlightedItem.maxProfit = profit;
			highlightedItem.itemValue = itemValue;
			highlightedItem.currentStock = shopStock;
			highlightedItem.costPrice = costPrice;
			highlightedItem.currentBuyPrice = buyPrice;
			highlightedItem.currentSellPrice = sellPrice;

			for (int qty : new int[]{1, 5, 10, 50}) 
			{
				highlightedItem.profits.put(qty, shop.getProfit(qty, itemId, shopStock, costPrice, itemValue));
				highlightedItem.revenues.put(qty, shop.getRevenue(qty, itemId, shopStock, itemValue));
			}

			highlightedItem.shop = shop;
			
        }
	}
	
	private static String getVersion()
	{
		try (InputStream in = HaggleHelperPlugin.class.getResourceAsStream("/runelite-plugin.properties"))
		{
			if (in == null)
			{
				throw new RuntimeException("runelite-plugin.properties not found");
			}

			Properties props = new Properties();
			props.load(in);
			return props.getProperty("version");
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to load plugin version", e);
		}
	}

	public void clearHighlights()
	{
		highlightedItems.clear();

		for (int itemId : trackedItemsManager.getTrackedItemIds())
		{
			HighlightedItem highlightedItem = new HighlightedItem(itemId);
			highlightedItems.put(itemId, highlightedItem);

			ItemComposition item = client.getItemDefinition(itemId);
			highlightedItems.put(item.getLinkedNoteId(), highlightedItem);
		}
	}

	public void rebuildHighlights()
	{
		clearHighlights();

		// If shop interface already open we need to immediately repopulate from stock values
		Widget items = client.getWidget(InterfaceID.Shopmain.ITEMS);
		Widget[] children;
		if (items != null && (children = items.getDynamicChildren()) != null)
		{
			getStock(
				Arrays.stream(children).map(
					item -> new Item(item.getItemId(), item.getItemQuantity())
				).toArray(Item[]::new)
			);
		}
	}
	
	public static String formatGp(int gp)
    {
        if (gp >= 10_000_000) return String.format("%,.2fM", gp / 1_000_000.0);
        if (gp >= 10_000) return String.format("%,.2fK", gp / 1_000.0);
        return String.format("%,d", gp);
    }

	@Subscribe
    protected void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals(HaggleHelperConfig.GROUP))
        {
            return;
        }

        clientThread.invoke(this::rebuildHighlights);
    }

	@Subscribe
	protected void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != INVENTORY_CONTAINER_ID && client.getWidget(InterfaceID.Shopmain.ITEMS) != null) 
		{
			log.debug(
				"Shop container changed: event={} ItemContainerId={} ContainerId={} items={}",
				event, event.getItemContainer().getId(), event.getContainerId(), event.getItemContainer().getItems()
			);

			getStock(event.getItemContainer().getItems());
		}
	}

	@Subscribe
    protected void onMenuOptionClicked(MenuOptionClicked event) {
        if (!config.blockUnprofitable()) 
		{
			return;
		}

		if (client.getWidget(InterfaceID.Shopmain.ITEMS) == null) 
		{
			return;
		}

		String menuOption = event.getMenuOption().replaceAll("<.*>", "");
		if (!Shop.MENU_OPTIONS.contains(menuOption)) 
		{
			return;
		}

		Widget eventWidget = event.getWidget();
		if (eventWidget == null) 
		{
			return;
		}

		int eventItemId = event.getItemId();
		HighlightedItem item = highlightedItems.get(eventItemId);
		if (item == null)
		{
			return;
		}

		// in the case of a noted item `eventItemId` will be different to `item.itemId`
		int itemId = item.itemId;
			
		final int sellAmount = Integer.parseInt(menuOption.replaceAll("\\D", ""));
		final int queued = queuedSales.getOrDefault(itemId, 0);
		int profit = item.profits.get(sellAmount);
		int profitDelta = item.maxProfit - profit;
		int numProfitable = item.numProfitable;

		if (queued > 0) 
		{
			log.debug("Transaction while queued, queued={} sellAmount={} itemId={}", queued, sellAmount, itemId);

			numProfitable -= queued;

			int virtualStock = item.currentStock + queued;
			profit = item.shop.getProfit(sellAmount, itemId, virtualStock, item.costPrice, item.itemValue);
			profitDelta = item.shop.getProfitDelta(sellAmount, itemId, virtualStock, item.costPrice, item.itemValue, config.profitThreshold());
		}
		
		if (profit > sellAmount*config.profitThreshold() && (sellAmount <= numProfitable || profitDelta <= config.bulkLossAllowance())) 
		{
			log.debug("Allowing profitable transaction: sellAmount={} queued={} profit={} profitDelta={} item={}", sellAmount, queued, profit, profitDelta, item);
			overlayPanel.addProfit(profit);
			queuedSales.put(itemId, queued + sellAmount);
			return;
		}

		event.consume();
		log.debug("Blocked transaction: sellAmount={} queued={} profit={} profitDelta={} item={}", sellAmount, queued, profit, profitDelta, item);
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE, 
			"", 
			String.format("[Haggle Helper]<col=ff0000> Blocked unprofitable transaction:</col> %s %s", menuOption, event.getMenuTarget()), 
			null
		);
    }

	@Subscribe
	protected void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() != overlayPanel)
		{
			return;
		}

		if (event.getEntry().getOption().equals("Reset"))
		{
			overlayPanel.reset();
		}
	}

	@Subscribe
    protected void onWidgetClosed(WidgetClosed event) {
        if (event.getGroupId() == InterfaceID.SHOPMAIN) {
            log.debug("Shop closed");
			queuedSales.clear();
			clearHighlights();
        }
    }
		
	@Provides
	HaggleHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HaggleHelperConfig.class);
	}
}

package com.hagglehelper;

import com.google.inject.Provides;
import com.hagglehelper.HaggleHelperConfig.InterfaceMode;
import com.hagglehelper.Shop.UnprofitableTransactionException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Type;
import java.awt.image.BufferedImage;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.widgets.Widget;
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
	private HaggleHelperConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
    private OverlayManager overlayManager;

    @Inject
    private HaggleHelperOverlay inventoryOverlay;

    @Inject
    private HaggleHelperOverlayPanel overlayPanel;

	@Inject
	private Gson gson;

	@Inject
	private HaggleHelperPanel panel;

	@Inject
	private HighlightedItemsManager highlightedItemsManager;

	private NavigationButton navButton;

	public Shop shop;

	private static final String SHOPS_RESOURCE = "shops.json";
	private static final Type SHOP_TYPE = new TypeToken<Map<String, Shop>>(){}.getType();
	private static final int INVENTORY_CONTAINER_ID = 93;
	
	public Map<Integer, TrackedItem> trackedItems;
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
			this.shopsMap.forEach((name, shop) -> shop.name = name);
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
		shop = null;

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

		highlightedItemsManager.clear();

		log.debug("Haggle Helper stopped!");
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

	public static String formatGp(int gp)
    {
        if (gp >= 10_000_000) return String.format("%,.2fM", gp / 1_000_000.0);
        if (gp >= 10_000) return String.format("%,.2fK", gp / 1_000.0);
        return String.format("%,d", gp);
    }

	@Subscribe
    protected void onConfigChanged(ConfigChanged event)
    {
        if (event.getGroup().equals(HaggleHelperConfig.GROUP))
        {
			highlightedItemsManager.clear();
        }
    }

	@Subscribe
	protected void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != INVENTORY_CONTAINER_ID && client.getWidget(InterfaceID.Shopmain.ITEMS) != null) 
		{
			if (shop == null)
			{
				Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
				if (frame == null || frame.isHidden())
				{
					return;
				}

				String shopName = frame.getDynamicChildren()[1].getText();
				log.debug("Getting shop with shopsName={}", shopName);
				shop = shopsMap.get(shopName);
				injector.injectMembers(shop);
				log.debug("Setting shop={}", shop);
			}

			log.debug(
				"Shop container changed: event={} ItemContainerId={} ContainerId={} items={}",
				event, event.getItemContainer().getId(), event.getContainerId(), event.getItemContainer().getItems()
			);

			shop.updateStock(event.getItemContainer().getItems());
			highlightedItemsManager.clear();
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

		Widget eventWidget = event.getWidget();
		if (eventWidget == null) 
		{
			return;
		}

		int eventItemId = event.getItemId();
		
		String menuOption = event.getMenuOption().replaceAll("<.*>", "");
		HighlightedItem item;
		if (menuOption.contains("Buy")) 
		{
			item = highlightedItemsManager.getOrCreate(eventItemId, InterfaceMode.SHOP);
		}
		else if (menuOption.contains("Sell"))
		{
			item = highlightedItemsManager.getOrCreate(eventItemId, InterfaceMode.INVENTORY);
		}
		else
		{
			return;
		}

		if (item == null)
		{
			return;
		}
		
        if (item.mode != config.interfaceMode() && config.interfaceMode() != InterfaceMode.BOTH)
        {
            return;
        }

		try {
			overlayPanel.addProfit(shop.processTransaction(menuOption, item));
		} catch (UnprofitableTransactionException e) {
			event.consume();
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE, 
				"", 
				String.format("[Haggle Helper]<col=ff0000> Blocked unprofitable transaction:</col> %s %s", menuOption, event.getMenuTarget()), 
				null
			);
		}
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
			highlightedItemsManager.clear();
			shop.queue.clear();
			shop = null;
        }
    }
		
	@Provides
	HaggleHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HaggleHelperConfig.class);
	}
}

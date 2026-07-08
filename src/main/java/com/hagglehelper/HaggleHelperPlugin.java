package com.hagglehelper;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.hagglehelper.HaggleHelperConfig.InterfaceMode;
import com.hagglehelper.HaggleHelperConfig.OverlayMode;
import com.hagglehelper.Shop.UnprofitableTransactionException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
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
	private static final String SHOPS_RESOURCE = "com/hagglehelper/shops.json";
	private static final Type SHOP_TYPE = new TypeToken<Map<String, Shop>>()
	{
	}.getType();
	private static final Map<Integer, String> PROBLEM_INVENTORY_IDS = ImmutableMap
		.<Integer, String>builder()
		.put(InventoryID.MAGICGUILDSHOP, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP_UIM, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP_GIM, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP2, "Magic Guild Store (Mystic Robes)")
		.put(InventoryID.MAGICGUILDSHOP2_SKILLCAPE, "Magic Guild Store (Mystic Robes)")
		.put(InventoryID.MAGICGUILDSHOP2_SKILLCAPE_TRIMMED, "Magic Guild Store (Mystic Robes)")
		.put(InventoryID.SKILL_GUIDE_CRAFTING_WEAVING, "Ned's Handmade Rope (100% Wool)")
		.put(InventoryID.XBOWS_SHOP, "Crossbow Shop (Dwarven Mine)")
		// .put(InventoryID.XBOWS_SHOP,"Crossbow Shop (White Wolf Mountain)")
		.put(InventoryID.XBOWS_SHOP_ADDY, "Crossbow Shop (Keldagrim)")
		.build();

	public static String VERSION;

	public Shop shop;
	public Map<String, Shop> shopsMap = new HashMap<>();

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

	@Inject
	private TrackedItemsManager trackedItemsManager;

	@Inject
	private ClientThread clientThread;
	private NavigationButton navButton;

	private static String getVersion()
	{
		try (InputStream in = HaggleHelperPlugin.class
			.getClassLoader()
			.getResourceAsStream("com/hagglehelper/gradle.properties"))
		{
			if (in == null)
			{
				throw new RuntimeException("gradle.properties not found");
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
		if (gp >= 10_000_000)
		{
			return String.format("%,.2fM", gp / 1_000_000.0);
		}
		if (gp >= 10_000)
		{
			return String.format("%,.2fK", gp / 1_000.0);
		}
		return String.format("%,d", gp);
	}

	private Shop getShop(String shopName)
	{
		Shop foundShop = shopsMap.get(shopName);

		if (foundShop == null)
		{
			throw new RuntimeException(
				String.format("No shop found with shopName='%s'", shopName)
			);
		}

		injector.injectMembers(foundShop);
		log.debug("Found shop={}", foundShop);
		return foundShop;
	}

	private void initShop()
	{
		Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
		shop = frame != null && !frame.isHidden()
			? getShop(frame.getDynamicChildren()[1].getText())
			: null;
	}

	@Override
	protected void startUp() throws Exception
	{
		VERSION = getVersion();

		InputStream stream = getClass().getClassLoader().getResourceAsStream(SHOPS_RESOURCE);
		if (stream == null)
		{
			throw new IllegalArgumentException("Resource not found.");
		}
		try (InputStreamReader reader = new InputStreamReader(stream))
		{
			this.shopsMap = gson.fromJson(reader, SHOP_TYPE);
			this.shopsMap.forEach((name, shop) -> shop.name = name);
		}
		catch (IOException e)
		{
			log.error("Failed to read JSON file \"{}\": {}", SHOPS_RESOURCE, e.getMessage());
		}

		panel = injector.getInstance(HaggleHelperPanel.class);
		panel.init();
		final BufferedImage icon = ImageUtil.loadImageResource(HaggleHelperPlugin.class,
			"icons/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Haggle Helper")
			.icon(icon)
			.priority(11)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(inventoryOverlay);
		overlayManager.add(overlayPanel);

		clientThread.invoke(this::initShop);

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
		int containerId = event.getContainerId();
		if (containerId != InventoryID.INV && client.getWidget(
			InterfaceID.Shopmain.ITEMS) != null)
		{
			log.debug(
				"Shop container changed: event={} ItemContainerId={} ContainerId={} items={}",
				event, event.getItemContainer().getId(), containerId, event
					.getItemContainer().getItems()
			);

			if (shop == null)
			{
				log.error("Shopmain.ITEMS changed with no shop!");
				return;
			}
			if (shop.updateStock(event.getItemContainer().getItems(), containerId))
			{
				highlightedItemsManager.clear();
			}
		}
	}

	@Subscribe
	protected void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (config.blockUnprofitable() == OverlayMode.NONE)
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
		if (config.blockUnprofitable() == OverlayMode.TRACKED && !trackedItemsManager
			.isTrackedItemId(eventItemId))
		{
			return;
		}

		String menuOption = event.getMenuOption().replaceAll("<.*>", "");
		HighlightedItem item;
		if (menuOption.contains("Buy "))
		{
			item = highlightedItemsManager.getOrCreate(eventItemId, InterfaceMode.SHOP);
		}
		else if (menuOption.contains("Sell "))
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

		try
		{
			overlayPanel.addProfit(shop.processTransaction(menuOption, item));
		}
		catch (UnprofitableTransactionException e)
		{
			event.consume();
			client.addChatMessage(
				ChatMessageType.GAMEMESSAGE,
				"",
				String.format(
					"[Haggle Helper]<col=ff0000> Blocked unprofitable transaction:</col> %s %s",
					menuOption, event.getMenuTarget()),
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
	protected void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SHOPMAIN)
		{
			log.debug("Shop closed");
			highlightedItemsManager.clear();
			shop.queue.clear();
			shop = null;
		}
	}

	@Subscribe
	protected void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != VarClientID.HELPER_GENERIC_TYPE_44)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		log.debug("Shop init script running with args={}", Arrays.toString(args));

		shop = getShop(
			PROBLEM_INVENTORY_IDS.getOrDefault(
				(int) args[1],
				(String) args[2]
			)
		);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.menuEntry())
		{
			return;
		}

		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		for (MenuEntry entry : event.getMenuEntries())
		{
			Widget widget = entry.getWidget();
			if (widget == null)
			{
				continue;
			}

			final int itemId = widget.getItemId();
			if (itemId == -1)
			{
				continue;
			}

			if (!"Examine".equals(entry.getOption()) || entry.getIdentifier() != 10)
			{
				continue;
			}

			client.getMenu().createMenuEntry(-1)
				.setOption("Track item")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					clientThread.invoke(() ->
					{
						TrackedItem item = trackedItemsManager.addItem(itemId);
						SwingUtilities.invokeLater(panel::refreshList);
						client.addChatMessage(
							ChatMessageType.GAMEMESSAGE,
							"",
							String.format(
								"[Haggle Helper] Added tracked item: <col=ff9040><b>%s</b></col> (<col=ffff00>%,d</col> gp)",
								item.getName(), item.getCost()
							),
							null
						);
					});
				});

			// Only add one entry
			break;
		}
	}

	@Provides
	HaggleHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HaggleHelperConfig.class);
	}
}

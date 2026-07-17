package com.hagglehelper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Haggle Helper"
)
public class HaggleHelperPlugin extends Plugin
{
	@RequiredArgsConstructor
	private static final class ShopOverride
	{
		private final Map<String, String> shops;
		private final BooleanSupplier enabled;

		String apply(String shopName)
		{
			return shops.containsKey(shopName) && enabled.getAsBoolean()
				? shops.get(shopName)
				: shopName;
		}
	}

	private static final String SHOPS_RESOURCE = "com/hagglehelper/shops.json";
	private static final Type SHOP_TYPE = new TypeToken<Map<String, Shop>>()
	{
	}.getType();

	private static final Map<String, String> KARAMJA_EASY_SHOPS = ImmutableMap
		.<String, String>builder()
		.put("Davon's Amulet Store.", "Davon's Amulet Store.(Karamja gloves)")
		.put("Fernahei's Fishing Hut.", "Fernahei's Fishing Hut.(Karamja gloves)")
		.put("Obli's General Store.", "Obli's General Store.(Karamja gloves)")
		.put("Tamayu's Spear Stall ", "Tamayu's Spear Stall(Karamja gloves)")
		.build();
	private static final ImmutableSet<Integer> KARAMJA_EASY_GLOVES = ImmutableSet.of(
		ItemID.ATJUN_GLOVES_EASY,
		ItemID.ATJUN_GLOVES_MED,
		ItemID.ATJUN_GLOVES_HARD,
		ItemID.ATJUN_GLOVES_ELITE
	);

	private static final Map<String, String> KARAMJA_HARD_SHOPS = ImmutableMap
		.<String, String>builder()
		.put("Karamja General Store", "Karamja General Store(Karamja gloves)")
		.put("Jiminua's Jungle Store.", "Jiminua's Jungle Store.(Karamja gloves 3+)")
		.build();
	private static final ImmutableSet<Integer> KARAMJA_HARD_GLOVES = ImmutableSet.of(
		ItemID.ATJUN_GLOVES_HARD,
		ItemID.ATJUN_GLOVES_ELITE
	);

	private static final Map<String, String> LUNAR_DIPLOMACY_SHOPS = ImmutableMap.of(
		"Baba Yaga's Magic Shop.", "Baba Yaga's Magic Shop.(post-quest)"
	);

	private static final Map<String, String> CONTACT_SHOPS = ImmutableMap.of(
		"Raetul and Co's Cloth Store.", "Raetul and Co's Cloth Store.(after Contact!)"
	);

	private static final Map<String, String> ENTER_THE_ABYSS_SHOPS = ImmutableMap.of(
		"Battle Runes", "Battle Runes(Enter the Abyss)"
	);

	private static final Map<Integer, String> PROBLEM_INVENTORY_IDS = ImmutableMap
		.<Integer, String>builder()
		.put(InventoryID.MAGICGUILDSHOP, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP_UIM, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP_GIM, "Magic Guild Store (Runes and Staves)")
		.put(InventoryID.MAGICGUILDSHOP2, "Magic Guild Store (Mystic Robes)")
		.put(InventoryID.MAGICGUILDSHOP2_SKILLCAPE, "Magic Guild Store (Mystic Robes)")
		.put(InventoryID.MAGICGUILDSHOP2_SKILLCAPE_TRIMMED, "Magic Guild Store (Mystic Robes)")
		.put(286, "Ned's Handmade Rope (100% Wool)(Fremennik Isles)")
		.put(InventoryID.XBOWS_SHOP, "Crossbow Shop (Dwarven Mine)")
		// .put(InventoryID.XBOWS_SHOP,"Crossbow Shop (White Wolf Mountain)")
		.put(InventoryID.XBOWS_SHOP_ADDY, "Crossbow Shop (Keldagrim)")
		.put(InventoryID.BAKERY, "Ardougne Baker's Stall.(west)")
		.put(InventoryID.BAKERY2, "Ardougne Baker's Stall.(east)")
		.put(InventoryID.MM_SCIMITAR_SHOP, "Daga's Scimitar Smithy")
		.put(InventoryID.MM_SCIMITAR_SHOP2, "Daga's Scimitar Smithy(Monkey Madness I)")
		.put(InventoryID.FEUD_MORRISANES, "Ali's Discount Wares.(general)")
		.put(InventoryID.ROGUETRADER_ALIM_RUNERETAIL_INV, "Ali's Discount Wares.(Elemental runes)")
		.put(InventoryID.ROGUETRADER_ALIM_RUNEWHOLESALE_INV,
			"Ali's Discount Wares.(Catalytic runes)")
		.put(InventoryID.ROGUETRADER_ALIM_DEFENDBJ_INV,
			"Ali's Discount Wares.(Defensive blackjacks)")
		.put(InventoryID.ROGUETRADER_ALIM_ASSAULTBJ_INV,
			"Ali's Discount Wares.(Offensive blackjacks)")
		.put(InventoryID.ROGUETRADER_ALIM_MEANPCLOTHES_INV, "Ali's Discount Wares.(menaphite gear)")
		.put(InventoryID.ROGUETRADER_ALIM_CARPETCLOTHES_INV, "Ali's Discount Wares.(desert gear)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY1, "White Knight Armoury(Novice)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY2, "White Knight Armoury(Peon)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY3, "White Knight Armoury(Page)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY4, "White Knight Armoury(Noble)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY5, "White Knight Armoury(Adept)")
		.put(InventoryID.WHITEKNIGHT_ARMOURY6, "White Knight Armoury(Master)")
		.put(InventoryID.HUNDRED_FOODCHEST1, "Culinaromancer's Chest(food, 0 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST2, "Culinaromancer's Chest(food, 1 Subquest)")
		.put(InventoryID.HUNDRED_FOODCHEST3, "Culinaromancer's Chest(food, 2 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST4, "Culinaromancer's Chest(food, 3 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST5, "Culinaromancer's Chest(food, 4 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST6, "Culinaromancer's Chest(food, 5 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST7, "Culinaromancer's Chest(food, 6 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST8, "Culinaromancer's Chest(food, 7 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST9, "Culinaromancer's Chest(food, 8 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST10, "Culinaromancer's Chest(food, full)")
		.put(InventoryID.HUNDRED_REWARDCHEST1, "Culinaromancer's Chest(0 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST2, "Culinaromancer's Chest(1 Subquest)")
		.put(InventoryID.HUNDRED_REWARDCHEST3, "Culinaromancer's Chest(2 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST4, "Culinaromancer's Chest(3 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST5, "Culinaromancer's Chest(4 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST6, "Culinaromancer's Chest(5 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST7, "Culinaromancer's Chest(6 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST8, "Culinaromancer's Chest(7 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST9, "Culinaromancer's Chest(8 Subquests)")
		.put(InventoryID.HUNDRED_REWARDCHEST10, "Culinaromancer's Chest(full)")
		.put(InventoryID.HUNDRED_FOODCHEST1_UIM, "Culinaromancer's Chest(food, 0 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST1_GIM, "Culinaromancer's Chest(food, 0 Subquest)")
		.put(InventoryID.HUNDRED_FOODCHEST2_UIM, "Culinaromancer's Chest(food, 1 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST2_GIM, "Culinaromancer's Chest(food, 1 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST3_UIM, "Culinaromancer's Chest(food, 2 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST3_GIM, "Culinaromancer's Chest(food, 2 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST4_UIM, "Culinaromancer's Chest(food, 3 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST4_GIM, "Culinaromancer's Chest(food, 3 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST5_UIM, "Culinaromancer's Chest(food, 4 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST5_GIM, "Culinaromancer's Chest(food, 4 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST6_UIM, "Culinaromancer's Chest(food, 5 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST6_GIM, "Culinaromancer's Chest(food, 5 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST7_UIM, "Culinaromancer's Chest(food, 6 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST7_GIM, "Culinaromancer's Chest(food, 6 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST8_UIM, "Culinaromancer's Chest(food, 7 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST8_GIM, "Culinaromancer's Chest(food, 7 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST9_UIM, "Culinaromancer's Chest(food, 8 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST9_GIM, "Culinaromancer's Chest(food, 8 Subquests)")
		.put(InventoryID.HUNDRED_FOODCHEST10_UIM, "Culinaromancer's Chest(food, full)")
		.put(InventoryID.HUNDRED_FOODCHEST10_GIM, "Culinaromancer's Chest(food, full)")
		.build();

	//TODO: add support for infinite-buy no-sell shops, like: The Flaming Arrow, Sunlight's Sanctum, The King's Inn

	private static final Pattern VALUE_PATTERN = Pattern.compile(
		"(.+): (currently costs|shop will buy for) ([\\d,]+) coins?\\.");
	private static final Pattern VALUE_FAIL = Pattern.compile(
		"You can't sell this.*");

	private static String VERSION;

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
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ErrorPopups errorPopups;

	private NavigationButton navButton;
	private Queue<Integer> pendingValueItemIds = new ArrayDeque<>();
	private final List<ShopOverride> shopOverrides = List.of(
		new ShopOverride(
			KARAMJA_EASY_SHOPS,
			() -> KARAMJA_EASY_GLOVES.contains(getGlovesItemId())
		),
		new ShopOverride(
			KARAMJA_HARD_SHOPS,
			() -> KARAMJA_HARD_GLOVES.contains(getGlovesItemId())
		),
		new ShopOverride(
			LUNAR_DIPLOMACY_SHOPS,
			() -> Quest.LUNAR_DIPLOMACY.getState(client) == QuestState.FINISHED
		),
		new ShopOverride(
			CONTACT_SHOPS,
			() -> Quest.CONTACT.getState(client) == QuestState.FINISHED
		),
		new ShopOverride(
			ENTER_THE_ABYSS_SHOPS,
			() -> Quest.ENTER_THE_ABYSS.getState(client) == QuestState.FINISHED
		)
	);

	public Map<Integer, Integer> inventoryMap = null;

	public static String getVersion()
	{
		if (VERSION == null)
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
				VERSION = props.getProperty("version");
			}
			catch (Exception e)
			{
				throw new RuntimeException("Failed to load plugin version", e);
			}
		}

		return VERSION;
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

	private Integer getGlovesItemId()
	{
		PlayerComposition composition = client.getLocalPlayer().getPlayerComposition();
		if (composition != null)
		{
			int gloves = composition.getEquipmentIds()[KitType.HANDS.getIndex()];
			if (gloves >= PlayerComposition.ITEM_OFFSET)
			{
				return gloves - PlayerComposition.ITEM_OFFSET;
			}
		}
		return null;
	}

	private Shop getShop(String shopName)
	{
		for (ShopOverride override : shopOverrides)
		{
			shopName = override.apply(shopName);
		}

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
		if (panel != null)
		{
			panel.deinit();
			clientToolbar.removeNavigation(navButton);
			panel = null;
			navButton = null;
		}

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
		if (containerId == InventoryID.INV)
		{
			@SuppressWarnings("null") Map<Integer, Integer> newInventoryMap = Arrays.stream(
				event.getItemContainer().getItems()
			)
				.filter(item -> item.getId() != -1)
				.collect(Collectors.toMap(
					item -> trackedItemsManager.getUnnotedId(item.getId()),
					Item::getQuantity,
					Integer::sum
				));

			if (inventoryMap != null)
			{
				@SuppressWarnings("null") Map<Integer, Integer> deltaMap = Sets.union(
					inventoryMap.keySet(),
					newInventoryMap.keySet()
				)
					.stream()
					.map(id -> Map.entry(
						id,
						newInventoryMap.getOrDefault(id, 0) - inventoryMap.getOrDefault(id, 0)
					))
					.filter(entry -> entry.getValue() != 0)
					.collect(
						Collectors.toMap(
							Map.Entry::getKey,
							Map.Entry::getValue
						)
					);

				if (client.getWidget(InterfaceID.Shopmain.ITEMS) != null && deltaMap.containsKey(
					ItemID.COINS))
				{
					log.debug("New shop trade delta={}", deltaMap.toString());
					int coins = deltaMap.remove(ItemID.COINS);

					int revenue = deltaMap.entrySet().stream()
						.mapToInt(entry ->
						{
							int itemId = entry.getKey();
							int delta = entry.getValue();
							int stock = shop.getStock(itemId) + delta;
							int itemValue = itemManager.getItemComposition(itemId).getPrice();

							return delta < 0
								? shop.getRevenueSellTo(-delta, itemId, itemValue, stock)
								: -shop.getRevenueBuyFrom(delta, itemId, itemValue, stock);
						})
						.sum();

					log.debug("expected revenue={}, observed revenue={}", revenue, coins);
					if (revenue != coins)
					{
						int differenceSum = deltaMap.entrySet().stream()
							.mapToInt(entry -> (int) (itemManager.getItemComposition(entry.getKey())
								.getPrice() * entry.getValue() * shop.changePer / 100.0))
							.sum();

						if (differenceSum == revenue - coins)
						{
							log.debug(
								"Stock refresh during trade check, delta={}, revenue={} coins={} differenceSum={}",
								deltaMap, revenue, coins, differenceSum);
						}
						else
						{
							errorPopups.tradeMismatch(deltaMap, revenue, coins);
							log.error(
								"Trade mismatch! delta={}, expected revenue={}, observed revenue={}",
								deltaMap, revenue, coins
							);
						}
					}
				}
				else if (!deltaMap.isEmpty())
				{
					log.debug("New inventory delta={}", deltaMap.toString());
				}
			}
			inventoryMap = newInventoryMap;
		}
		else if (client.getWidget(InterfaceID.Shopmain.ITEMS) != null)
		{
			log.debug(
				"Shop container changed: event={} ItemContainerId={} ContainerId={} items={}",
				event, event.getItemContainer().getId(), containerId, event
					.getItemContainer().getItems()
			);

			Item[] items = event.getItemContainer().getItems();
			if (shop == null)
			{
				log.error("Shopmain.ITEMS changed with no shop!");
				if (config.errorReports())
				{
					Widget frame = client.getWidget(InterfaceID.Shopmain.FRAME);
					if (frame != null)
					{
						String shopName = frame.getDynamicChildren()[1].getText();
						errorPopups.unknownShop(containerId, shopName, items);
					}
				}
				return;
			}
			if (shop.updateStock(items, containerId))
			{
				highlightedItemsManager.clear();
			}
		}
	}

	@Subscribe
	protected void onMenuOptionClicked(MenuOptionClicked event)
	{
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
		String menuOption = Text.removeTags(event.getMenuOption());
		log.debug("Shop menu option clicked event: menuOption={} eventItemId={}", menuOption,
			eventItemId);

		if (menuOption.equals("Value"))
		{
			log.debug("Adding to pendingValueItemIds queue: itemId={}", eventItemId);
			pendingValueItemIds.offer(eventItemId);
			return;
		}

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
			if (config.blockUnprofitable() == OverlayMode.NONE)
			{
				return;
			}

			if (config.blockUnprofitable() == OverlayMode.TRACKED && !trackedItemsManager
				.isTrackedItemId(eventItemId))
			{
				return;
			}

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
			overlayPanel.resetProfit();
		}
	}

	@Subscribe
	protected void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SHOPMAIN)
		{
			log.debug("Shop closed");
			highlightedItemsManager.clear();
			pendingValueItemIds.clear();
			shop.queue.clear();
			shop.currentStocks.clear();
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

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (pendingValueItemIds.isEmpty())
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());
		Matcher matcher = VALUE_PATTERN.matcher(message);
		if (matcher.matches())
		{
			int itemId = pendingValueItemIds.poll();
			String itemName = matcher.group(1);

			if (!itemManager.getItemComposition(itemId).getName().equals(itemName))
			{
				log.error(
					"Item name mismatched while handling value chat message, itemId={} itemName={}",
					itemId, itemName
				);
				pendingValueItemIds.clear();
				return;
			}

			int value = Integer.parseInt(matcher.group(3).replace(",", ""));

			HighlightedItem item;
			int price;
			String mode;
			switch (matcher.group(2))
			{
				case "currently costs":
					item = highlightedItemsManager.get(itemId, InterfaceMode.SHOP);
					price = shop.getItemPriceBuyFrom(item);
					mode = "buying from";
					log.debug("Shop sells {} for {} (calculated price={})", itemName, value, price);
					break;

				case "shop will buy for":
					item = highlightedItemsManager.get(itemId, InterfaceMode.INVENTORY);
					price = shop.getItemPriceSellTo(item);
					mode = "selling to";
					log.debug("Shop buys {} for {} (calculated price={})", itemName, value, price);
					break;

				default:
					return;
			}

			if (price != value)
			{
				log.error("Price & value mismatched! itemId={} price={} value={} shop={}",
					itemId, price, value, shop);

				if (config.errorReports())
				{
					errorPopups.priceMismatch(item, itemName, value, price, mode);
				}
			}
		}
		else if (VALUE_FAIL.matcher(message).matches())
		{
			pendingValueItemIds.poll();
		}
	}

	@Provides
	boolean provideLumbridgeElite()
	{
		return client.getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE) == 1;
	}

	@Provides
	Map<Integer, Integer> provideInventoryMap()
	{
		return inventoryMap;
	}

	@Provides
	HaggleHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HaggleHelperConfig.class);
	}
}

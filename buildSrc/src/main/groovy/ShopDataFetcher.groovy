import org.json.JSONObject
import groovy.transform.CompileStatic

import java.util.concurrent.CompletableFuture
import java.util.regex.Matcher

/**
 * Fetches shop and item stock data from the Old School RuneScape Wiki bucket API
 * and transforms it into the format consumed by HaggleHelper.
 */
@CompileStatic
class ShopDataFetcher {

    static final String WIKI_BASE = 'https://oldschool.runescape.wiki/api.php?action=bucket&format=json'
    static final String USER_AGENT_FORMAT = 'HaggleHelper/%s (gradle build task)'
    static final int PAGE_SIZE = 5000
    static final Map<String, Set<String>> EXPECTED_DUPLICATE_SHOPS = [
            'Magic Guild Store' : [
                    'Magic Guild Store (Runes and Staves)',
                    'Magic Guild Store (Mystic Robes)'
            ] as Set<String>,
            'Crossbow Shop'     : [
                    'Crossbow Shop (White Wolf Mountain)',
                    'Crossbow Shop (Dwarven Mine)',
                    'Crossbow Shop (Keldagrim)'
            ] as Set<String>,
            'Ratpit Bar'        : [
                    'Ratpit bar (Port Sarim)',
                    'Ratpit bar (Keldagrim)',
                    'Ratpit bar (Varrock)'
            ] as Set<String>,
            'Bounty Hunter Shop': [
                    'Bounty Hunter Shop (Deadman Mode)',
                    'Bounty Hunter Shop (historical)'
            ] as Set<String>
    ]

    private static String userAgent

    static void writeShopsJson(String version, File file) {
        userAgent = String.format(USER_AGENT_FORMAT, version)

        CompletableFuture<Map<String, String>> allShopsFuture = CompletableFuture.supplyAsync {
            fetchAllShops()
        }

        CompletableFuture<Set<String>> generalStoresFuture = CompletableFuture.supplyAsync {
            fetchGeneralStores()
        }

        CompletableFuture<List<Map<String, Object>>> storeLinesFuture = CompletableFuture.supplyAsync {
            fetchStoreLines()
        }

        CompletableFuture<Map<String, Integer>> itemIdsFuture = CompletableFuture.supplyAsync {
            fetchItemIds()
        }

        Set<String> generalStores = generalStoresFuture.join()
        println "Fetched ${generalStores.size()} general stores"

        List<Map<String, Object>> storeLines = storeLinesFuture.join()
        println "Fetched ${storeLines.size()} storeline rows"

        Map<String, Integer> itemIdMap = itemIdsFuture.join()
        println "Resolved ${itemIdMap.size()} item IDs"

        Map<String, String> shopMap = allShopsFuture.join()
        println "Mapped ${shopMap.size()} differing shop names"

        Map<String, Object> shops = makeShopMap(storeLines, itemIdMap, generalStores, shopMap)

        println "Built ${shops.size()} shops"

        file.text = new JSONObject(shops).toString(1)
        println String.format('Written to %s (%.2f KiB)', file.path, file.length() / 1024.0)
    }

    private static List<Map<String, Object>> fetchStoreLines() {
        List<Map<String, Object>> allLines = []
        int offset = 0

        while (true) {
            String query = URLEncoder.encode(
                    "bucket('storeline')" +
                            '.select(' +
                            "'sold_by'," +
                            "'sold_item'," +
                            "'store_stock'," +
                            "'store_currency'," +
                            "'store_sell_multiplier'," +
                            "'store_buy_multiplier'," +
                            "'store_delta'" +
                            ')' +
                            ".where('store_currency','=','Coins')" +
                            ".where('store_stock','>=','0')" +
                            ".where('store_stock','!=','N/A')" +
                            ".limit(${PAGE_SIZE})" +
                            ".offset(${offset})" +
                            '.run()',
                    'UTF-8'
            )

            List<Map<String, Object>> rows = fetchBucket(query)

            allLines.addAll(rows)

            if (rows.size() < PAGE_SIZE) {
                break
            }

            offset += PAGE_SIZE
        }

        return allLines
    }

    private static Map<String, String> fetchAllShops() throws IllegalStateException {
        Map<String, String> shopMap = [:]
        Map<String, String> pageMap = [:]
        Map<String, Set<String>> dupeMap = [:]

        int offset = 0

        while (true) {
            String query = URLEncoder.encode(
                    "bucket('infobox_shop')" +
                            ".select('shop_name', 'page_name')" +
                            ".where('shop_name','!=','page_name')" +
                            ".limit(${PAGE_SIZE})" +
                            ".offset(${offset})" +
                            '.run()',
                    'UTF-8'
            )

            List<Map<String, Object>> rows = fetchBucket(query)

            rows.each {
                Map<String, Object> row ->
                    {
                        String shop = row.shop_name
                        String page = row.page_name

                        if (shop == null || page == null) {
                            println "Null name: page_name='${page}', shop_name='${shop}'"
                            return
                        }

                        if (shop == page) {
                            return
                        }

                        if (shopMap.containsKey(page)) {
                            return
                        }

                        if (EXPECTED_DUPLICATE_SHOPS.containsKey(shop)
                                && EXPECTED_DUPLICATE_SHOPS[shop].contains(page)) {
                            return
                        }

                        if (dupeMap.containsKey(shop)) {
                            println "UNEXPECTED DUPLICATE - '${shop}': '${page}'"
                            dupeMap[shop].add(page)
                            return
                        }

                        if (pageMap.containsKey(shop)) {
                            println "UNEXPECTED DUPLICATE - '${shop}': '${page}'"
                            println "UNEXPECTED DUPLICATE - '${shop}': '${pageMap[shop]}'"
                            dupeMap[shop] = [] as Set<String>
                            dupeMap[shop].add(page)
                            String oldPage = pageMap.remove(shop)
                            dupeMap[shop].add(oldPage)
                            shopMap.remove(oldPage)
                            return
                        }

                        shopMap[page] = shop
                        pageMap[shop] = page
                    }
            }

            if (rows.size() < PAGE_SIZE) {
                break
            }

            offset += PAGE_SIZE
        }

        if (dupeMap.size() > 0) {
            throw new IllegalStateException('Failed to fetch shop data: unexpected duplicates found')
        }
        return shopMap
    }

    private static Set<String> fetchGeneralStores() {
        String text = URI.create(
                'https://oldschool.runescape.wiki/w/General_store?action=raw'
        ).toURL().getText('UTF-8')

        Set<String> stores = [] as Set<String>

        boolean inLocationsTable = false

        text.eachLine { String line ->
            if (line.startsWith('==Locations==')) {
                inLocationsTable = true
                return
            }

            if (!inLocationsTable) {
                return
            }

            if (line.startsWith('|}')) {
                inLocationsTable = false
                return
            }

            Matcher matcher = (line =~ /^\|\[\[([^|\]]+)/)

            if (matcher.find()) {
                stores << matcher.group(1)
            }
        }

        return stores
    }

    private static Map<String, Integer> fetchItemIds() {
        Map<String, Integer> itemIdMap = [:]
        int offset = 0

        while (true) {
            String query = URLEncoder.encode(
                    "bucket('infobox_item')" +
                            ".select('item_id','item_name','page_name','page_name_sub')" +
                            ".limit(${PAGE_SIZE})" +
                            ".offset(${offset})" +
                            '.run()',
                    'UTF-8'
            )

            List<Map<String, Object>> rows = fetchBucket(query)

            rows.each { Map<String, Object> row ->
                String name
                if (row.page_name == row.item_name) {
                    name = row.item_name
                } else if (row.page_name_sub == row.page_name) {
                    name = row.page_name
                } else {
                    name = row.item_name
                }

                if (!name) {
                    return
                }

                Integer itemId = null

                Object rawItemId = row.item_id

                if (rawItemId in List) {
                    List<?> itemIds = (List<?>) rawItemId

                    if (!itemIds.empty) {
                        itemId = parseItemId(itemIds[0])
                    }
                } else {
                    itemId = parseItemId(rawItemId)
                }

                if (itemId != null) {
                    itemIdMap.computeIfAbsent(name, key -> itemId)
                }
            }

            if (rows.size() < PAGE_SIZE) {
                break
            }

            offset += PAGE_SIZE
        }

        return itemIdMap
    }

    private static Map<String, Object> makeShopMap(
            List<Map<String, Object>> allLines,
            Map<String, Integer> itemIdMap,
            Set<String> generalStores,
            Map<String, String> shopMap
    ) {

        Map<String, Object> shops = [:]

        allLines.each { Map<String, Object> line ->
            String pageName = line.sold_by as String
            String itemName = line.sold_item as String

            String shopName = shopMap.getOrDefault(pageName, pageName)

            if (!shopName || !itemName) {
                return
            }

            Integer itemId = itemIdMap[itemName]

            if (itemId == null) {
                return
            }

            Integer stock = null

            try {
                stock = Integer.parseInt(line.store_stock.toString())
            }
            catch (NumberFormatException ignored) {
                // Skip malformed stock values.
                /* groovylint-disable-next-line ReturnNullFromCatchBlock */
                return
            }

            shops.computeIfAbsent(shopName) {
                [
                        buysAt       : parsePercent(line.store_buy_multiplier, 70),
                        sellsAt      : parsePercent(line.store_sell_multiplier, 100),
                        changePer    : parseDelta(line.store_delta, 3.0d),
                        isGeneral    : generalStores.contains(pageName),
                        defaultStocks: [:]
                ]
            }

            ((Map<String, Object>) shops[shopName]).defaultStocks[itemId.toString()] = stock
        }

        return shops
    }

    private static List<Map<String, Object>> fetchBucket(String encodedQuery) {
        URLConnection conn = URI.create("${WIKI_BASE}&query=${encodedQuery}")
                .toURL()
                .openConnection()

        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000

        conn.setRequestProperty('User-Agent', userAgent)
        conn.setRequestProperty('Accept', 'application/json')

        JSONObject response = new JSONObject(conn.inputStream.text)

        if (response.has('error')) {
            throw new IllegalStateException(
                "Wiki API error: ${response.get('error')} for query ${encodedQuery}"
            )
        }

        return response.optJSONArray('bucket')
            ?.toList()
            ?.collect { Object obj ->
                (Map<String, Object>) obj
            }
            ?: []
    }

    private static Integer parseItemId(Object value) {
        if (value == null) {
            return null
        }

        Integer result = null

        try {
            result = Integer.valueOf(value.toString())
        }
        catch (NumberFormatException ignored) {
        }

        return result
    }

    private static int parsePercent(Object value, int fallback) {
        String s = value as String

        if (!s.number) {
            return fallback
        }

        return Math.round(s.toDouble() / 10.0d) as int
    }

    private static double parseDelta(Object value, double fallback) {
        String s = value as String

        if (!s.number) {
            return fallback
        }

        return s.toDouble() / 10.0d
    }

}

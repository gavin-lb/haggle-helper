import groovy.transform.CompileStatic

import java.util.concurrent.CompletableFuture
import java.util.regex.Matcher
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Fetches shop and item stock data from the Old School RuneScape Wiki bucket API
 * and transforms it into the format consumed by HaggleHelper.
 */
@CompileStatic
class ShopDataFetcher {

    static final String WIKI_BASE = 'https://oldschool.runescape.wiki/api.php?action=bucket&format=json'
    static final String USER_AGENT_FORMAT = 'HaggleHelper/%s (gradle build task)'

    static final int PAGE_SIZE = 500
    static final int BATCH_SLEEP_MS = 100

    private static String userAgent

    static Map<String, Object> fetchAllShops(String version) {
        userAgent = String.format(USER_AGENT_FORMAT, version)

        CompletableFuture<Set<String>> generalStoresFuture = CompletableFuture.supplyAsync {
            fetchGeneralStores()
        }

        CompletableFuture<List<Map<String, Object>>> storeLinesFuture = CompletableFuture.supplyAsync {
            fetchAllStoreLines()
        }

        CompletableFuture<Map<String, Integer>> itemIdsFuture = CompletableFuture.supplyAsync {
            resolveItemIds()
        }

        Set<String> generalStores = generalStoresFuture.join()
        println "Fetched ${generalStores.size()} general stores"

        List<Map<String, Object>> storeLines = storeLinesFuture.join()
        println "Fetched ${storeLines.size()} storeline rows"

        Map<String, Integer> itemIdMap = itemIdsFuture.join()
        println "Resolved ${itemIdMap.size()} item IDs"

        Map<String, Object> shops = makeShopMap(storeLines, itemIdMap, generalStores)

        println "Built ${shops.size()} shops"

        return shops
    }

    private static List<Map<String, Object>> fetchAllStoreLines() {
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
            Thread.sleep(BATCH_SLEEP_MS)
        }

        return allLines
    }

    private static Map<String, Integer> resolveItemIds() {
        Map<String, Integer> itemIdMap = [:]
        int offset = 0

        while (true) {
            String query = URLEncoder.encode(
                "bucket('infobox_item')" +
                ".select('item_id','item_name','tradeable','page_name','page_name_sub')" +
                ".limit(${PAGE_SIZE})" +
                ".offset(${offset})" +
                '.run()',
                'UTF-8'
            )

            List<Map<String, Object>> rows = fetchBucket(query)

            rows.each { Map<String, Object> row ->
                String name = (row.page_name_sub ?: row.page_name ?: row.item_name) as String

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
                }
                else {
                    itemId = parseItemId(rawItemId)
                }

                if (itemId != null && row.tradeable != null) {
                    if (itemIdMap.containsKey(name)) {
                        println "Duplicate tradable item name: ${name} existingId=${itemIdMap[name]} newId=${itemId}"
                    } else {
                        itemIdMap[name] = itemId
                    }
                }
            }

            if (rows.size() < PAGE_SIZE) {
                break
            }

            offset += PAGE_SIZE
            Thread.sleep(BATCH_SLEEP_MS)
        }

        return itemIdMap
    }

    private static Map<String, Object> makeShopMap(
        List<Map<String, Object>> allLines,
        Map<String, Integer> itemIdMap,
        Set<String> generalStores
    ) {

        Map<String, Object> shops = [:]

        allLines.each { Map<String, Object> line ->
            String shopName = line.sold_by as String
            String itemName = line.sold_item as String

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
            }

            if (stock == null) {
                return
            }

            shops.computeIfAbsent(shopName) {
                [
                    buysAt: parsePercent(line.store_buy_multiplier, 70),
                    sellsAt: parsePercent(line.store_sell_multiplier, 100),
                    changePer: parseDelta(line.store_delta, 3.0d),
                    general: generalStores.contains(shopName.toUpperCase()),
                    defaultStocks: [:]
                ]
            }

            ((Map<String, Object>) shops[shopName]).defaultStocks[itemId.toString()] = stock
        }

        return shops
    }

    private static List<Map<String, Object>> fetchBucket(String encodedQuery) {
        String url = "${WIKI_BASE}&query=${encodedQuery}"

        URLConnection conn = new URL(url).openConnection()

        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000

        conn.setRequestProperty('User-Agent', userAgent)
        conn.setRequestProperty('Accept', 'application/json')

        Map<String, Object> response = new ObjectMapper().readValue(conn.inputStream.text, Map)

        if (response.error) {
            throw new IllegalStateException(
                "Wiki API error: ${response.error} for query ${encodedQuery}"
            )
        }

        return (List<Map<String, Object>>) (response.bucket ?: [])
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

    private static Set<String> fetchGeneralStores() {
        String text = new URL(
            'https://oldschool.runescape.wiki/w/General_store?action=raw'
        ).getText('UTF-8')

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
                stores << matcher.group(1).toUpperCase()
            }
        }

        return stores
    }

}

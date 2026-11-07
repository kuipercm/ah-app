package nl.nielskuiper.weeklyorder.service

import com.fasterxml.jackson.databind.ObjectMapper
import nl.nielskuiper.weeklyorder.model.AhCredential
import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.repository.AhCredentialRepository
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@Service
class AhApiService(
    private val credentialRepository: AhCredentialRepository,
    private val productRepository: ProductRepository
) {
    private val log = LoggerFactory.getLogger(AhApiService::class.java)
    private val restTemplate: RestTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(5000)
        setReadTimeout(15000)
    })
    private val objectMapper = ObjectMapper()
    private val searchCache = mutableMapOf<String, Pair<List<Product>, LocalDateTime>>()

    private var anonymousToken: String? = null
    private var anonymousTokenExpiresAt: LocalDateTime? = null

    companion object {
        const val AUTH_BASE_URL = "https://api.ah.nl/mobile-auth/v1/auth"
        const val SERVICES_BASE_URL = "https://api.ah.nl/mobile-services"
        const val CLIENT_ID = "appie"
        const val USER_AGENT = "Appie/8.22.3 Android/12"
    }

    fun getOAuthLoginUrl(): String =
        "https://login.ah.nl/secure/oauth/authorize?client_id=$CLIENT_ID&redirect_uri=appie://login-exit&response_type=code"

    fun getAuthStatus(): Map<String, Any?> {
        val opt = credentialRepository.findById(1L)
        return if (opt.isPresent && !opt.get().accessToken.isNullOrBlank()) {
            val cred = opt.get()
            mapOf(
                "authenticated" to true,
                "userName" to (cred.userName ?: "AH Gebruiker"),
                "expiresAt" to cred.expiresAt,
                "lastSyncedAt" to cred.lastSyncedAt
            )
        } else {
            mapOf(
                "authenticated" to false,
                "loginUrl" to getOAuthLoginUrl()
            )
        }
    }

    fun exchangeAuthCode(rawCodeOrUrl: String): Boolean {
        var code = rawCodeOrUrl.trim()
        if (code.contains("code=")) {
            code = code.substring(code.indexOf("code=") + 5)
            if (code.contains("&")) {
                code = code.substring(0, code.indexOf("&"))
            }
        }

        try {
            val headers = buildHeaders(null)
            val body = mapOf("clientId" to CLIENT_ID, "code" to code)
            val request = HttpEntity(body, headers)

            val response = restTemplate.postForEntity(
                "$AUTH_BASE_URL/token",
                request,
                String::class.java
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val root = objectMapper.readTree(response.body)
                val accessToken = root.path("access_token").asText()
                val refreshToken = root.path("refresh_token").asText()
                val expiresIn = root.path("expires_in").asInt(7200)

                val cred = credentialRepository.findById(1L).orElse(AhCredential())
                cred.accessToken = accessToken
                cred.refreshToken = refreshToken
                cred.expiresAt = LocalDateTime.now().plusSeconds(expiresIn.toLong())
                cred.lastSyncedAt = LocalDateTime.now()
                credentialRepository.save(cred)

                log.info("Successfully authenticated with Albert Heijn API")
                return true
            }
        } catch (e: Exception) {
            log.error("Failed to exchange AH auth code: {}", e.message)
        }
        return false
    }

    fun logout() {
        credentialRepository.findById(1L).ifPresent { credentialRepository.delete(it) }
    }

    @Synchronized
    fun getValidToken(requireUserToken: Boolean): String? {
        val opt = credentialRepository.findById(1L)
        if (opt.isPresent) {
            val cred = opt.get()
            if (!cred.accessToken.isNullOrBlank()) {
                if (cred.isExpired && cred.refreshToken != null) {
                    val refreshed = refreshUserToken(cred)
                    if (!refreshed) return null
                }
                return cred.accessToken
            }
        }

        if (requireUserToken) {
            return null
        }

        if (anonymousToken != null && anonymousTokenExpiresAt != null && LocalDateTime.now().isBefore(anonymousTokenExpiresAt)) {
            return anonymousToken
        }

        try {
            val headers = buildHeaders(null)
            val body = mapOf("clientId" to CLIENT_ID)
            val request = HttpEntity(body, headers)

            val res = restTemplate.postForEntity(
                "$AUTH_BASE_URL/token/anonymous",
                request,
                String::class.java
            )

            if (res.statusCode.is2xxSuccessful && res.body != null) {
                val root = objectMapper.readTree(res.body)
                anonymousToken = root.path("access_token").asText()
                val expiresIn = root.path("expires_in").asInt(7200)
                anonymousTokenExpiresAt = LocalDateTime.now().plusSeconds(expiresIn - 60L)
                return anonymousToken
            }
        } catch (e: Exception) {
            log.warn("Failed to obtain anonymous token from AH: {}", e.message)
        }

        return null
    }

    private fun refreshUserToken(cred: AhCredential): Boolean {
        try {
            val headers = buildHeaders(null)
            val body = mapOf("clientId" to CLIENT_ID, "refreshToken" to cred.refreshToken)
            val request = HttpEntity(body, headers)

            val response = restTemplate.postForEntity(
                "$AUTH_BASE_URL/token/refresh",
                request,
                String::class.java
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val root = objectMapper.readTree(response.body)
                cred.accessToken = root.path("access_token").asText()
                if (root.has("refresh_token")) {
                    cred.refreshToken = root.path("refresh_token").asText()
                }
                val expiresIn = root.path("expires_in").asInt(7200)
                cred.expiresAt = LocalDateTime.now().plusSeconds(expiresIn.toLong())
                credentialRepository.save(cred)
                log.info("Refreshed AH user token")
                return true
            }
        } catch (e: Exception) {
            log.error("Failed to refresh AH user token: {}", e.message)
        }
        return false
    }

    fun searchProducts(query: String?): List<Product> {
        if (query.isNullOrBlank()) return emptyList()

        val cacheKey = query.trim().lowercase()
        val cached = searchCache[cacheKey]
        if (cached != null && LocalDateTime.now().isBefore(cached.second)) {
            return cached.first
        }

        val token = getValidToken(false) ?: run {
            val fallback = searchProductsFallback(query)
            if (fallback.isNotEmpty()) searchCache[cacheKey] = Pair(fallback, LocalDateTime.now().plusHours(1))
            return fallback
        }

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
            val url = "$SERVICES_BASE_URL/product/search/v2?query=$encodedQuery&sortOn=RELEVANCE"

            val headers = buildHeaders(token)
            val request = HttpEntity<Void>(headers)

            val response = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val results = parseProductSearchResults(response.body!!)
                searchCache[cacheKey] = Pair(results, LocalDateTime.now().plusHours(1))
                return results
            }
        } catch (e: Exception) {
            log.warn("AH search failed for query '{}': {}. Falling back to cached products.", query, e.message)
        }

        val fallback = searchProductsFallback(query)
        if (fallback.isNotEmpty()) searchCache[cacheKey] = Pair(fallback, LocalDateTime.now().plusHours(1))
        return fallback
    }

    fun parseProductSearchResults(json: String): List<Product> {
        val results = mutableListOf<Product>()
        try {
            val root = objectMapper.readTree(json)
            val productsNode = root.path("products")
            if (productsNode.isArray) {
                for (node in productsNode) {
                    var webshopId = node.path("webshopId").asLong()
                    if (webshopId == 0L) {
                        webshopId = node.path("hqId").asLong()
                    }
                    if (webshopId == 0L) continue

                    val title = node.path("title").asText()
                    val brand = if (node.hasNonNull("brand")) node.path("brand").asText() else null
                    var unitSize = node.path("unitSize").asText("")
                    if (unitSize.isBlank()) {
                        unitSize = node.path("salesUnitSize").asText("")
                    }

                    var price = BigDecimal.valueOf(
                        node.path("priceBeforeBonus").asDouble(node.path("currentPrice").asDouble(0.0))
                    )
                    if (price.compareTo(BigDecimal.ZERO) == 0 && node.has("currentPrice")) {
                        price = BigDecimal.valueOf(node.path("currentPrice").asDouble())
                    }

                    val isBonus = node.path("isBonus").asBoolean(false)
                    val bonusMechanism = if (node.hasNonNull("bonusMechanism")) node.path("bonusMechanism").asText() else null
                    val bonusPrice = if (isBonus && node.has("currentPrice")) {
                        BigDecimal.valueOf(node.path("currentPrice").asDouble())
                    } else null

                    val imageUrl = node.path("images").firstOrNull()?.path("url")?.asText()

                    val product = productRepository.findById(webshopId).orElse(Product(webshopId = webshopId))
                    product.webshopId = webshopId
                    product.title = title
                    product.brand = brand
                    product.price = price
                    product.bonusPrice = bonusPrice
                    product.isBonus = isBonus
                    product.bonusMechanism = bonusMechanism
                    product.unitSize = unitSize
                    product.imageUrl = imageUrl
                    product.category = if (node.hasNonNull("category")) node.path("category").asText() else null

                    val saved = productRepository.save(product)
                    results.add(saved)
                }
            }
        } catch (e: Exception) {
            log.error("Error parsing AH products JSON: {}", e.message)
        }
        return results
    }

    private fun searchProductsFallback(query: String): List<Product> {
        return productRepository.findByTitleContainingIgnoreCase(query)
    }

    fun getActiveOrder(): Map<String, Any?> {
        val token = getValidToken(true) ?: return mapOf("hasActiveOrder" to false, "message" to "Geen AH account verbonden")

        try {
            val url = "$SERVICES_BASE_URL/order/v1/summaries/active?sortBy=DEFAULT"
            val headers = buildHeaders(token)
            val request = HttpEntity<Void>(headers)

            val res = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            if (res.statusCode.is2xxSuccessful && res.body != null) {
                val root = objectMapper.readTree(res.body)
                if (root.isArray && root.size() > 0) {
                    val order = root.get(0)
                    return mapOf(
                        "hasActiveOrder" to true,
                        "orderId" to order.path("id").asText(),
                        "deliveryDate" to order.path("deliveryDate").asText(),
                        "status" to order.path("status").asText(),
                        "totalPrice" to order.path("totalPrice").path("amount").asDouble(0.0)
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("Could not retrieve active order: {}", e.message)
        }

        return mapOf("hasActiveOrder" to false, "message" to "Geen actief bezorgmoment gevonden")
    }

    fun pushItemsToCart(items: List<Map<String, Any?>>): Map<String, Any?> {
        val token = getValidToken(true)
            ?: return mapOf("success" to false, "message" to "Verbind eerst je Albert Heijn account in Instellingen")

        return try {
            val activeOrder = getActiveOrder()
            if (activeOrder["hasActiveOrder"] == true) {
                val orderId = activeOrder["orderId"] as String
                pushToActiveOrder(orderId, items, token)
            } else {
                pushToShoppingList(items, token)
            }
        } catch (e: Exception) {
            log.error("Failed to push items to AH: {}", e.message)
            mapOf("success" to false, "message" to "Fout bij verzenden naar AH: ${e.message}")
        }
    }

    private fun pushToActiveOrder(orderId: String, items: List<Map<String, Any?>>, token: String): Map<String, Any?> {
        return try {
            val url = "$SERVICES_BASE_URL/order/v1/orders/$orderId/items"
            val headers = buildHeaders(token)

            val payload = items.map {
                mapOf("productId" to it["productId"], "quantity" to it["quantity"])
            }

            val request = HttpEntity(payload, headers)
            val res = restTemplate.postForEntity(url, request, String::class.java)

            if (res.statusCode.is2xxSuccessful) {
                mapOf("success" to true, "target" to "BESTELLING", "orderId" to orderId, "itemCount" to items.size)
            } else {
                pushToShoppingList(items, token)
            }
        } catch (e: Exception) {
            log.warn("Direct order push error: {}. Trying shopping list fallback.", e.message)
            pushToShoppingList(items, token)
        }
    }

    fun pushToShoppingList(items: List<Map<String, Any?>>, token: String): Map<String, Any?> {
        return try {
            val url = "$SERVICES_BASE_URL/shoppinglist/v2/items"
            val headers = buildHeaders(token)

            val payloadItems = items.map {
                mapOf("productId" to it["productId"], "quantity" to it["quantity"], "shoppingspot" to "SUPERMARKET")
            }

            val body = mapOf("items" to payloadItems)
            val request = HttpEntity(body, headers)

            val res = restTemplate.exchange(url, HttpMethod.PATCH, request, String::class.java)
            if (res.statusCode.is2xxSuccessful) {
                mapOf("success" to true, "target" to "MIJN_LIJST", "itemCount" to items.size)
            } else {
                mapOf("success" to false, "message" to "Kon niet toevoegen aan AH lijst")
            }
        } catch (e: Exception) {
            log.error("Shopping list push failed: {}", e.message)
            mapOf("success" to false, "message" to "Kon niet toevoegen aan AH mandje/lijst")
        }
    }

    fun getUserLists(): List<Map<String, Any>> {
        val token = getValidToken(true) ?: return emptyList()

        return try {
            val url = "$SERVICES_BASE_URL/lists/v3/lists"
            val headers = buildHeaders(token)
            val request = HttpEntity<Void>(headers)

            val res = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            if (res.statusCode.is2xxSuccessful && res.body != null) {
                val root = objectMapper.readTree(res.body)
                val lists = mutableListOf<Map<String, Any>>()
                if (root.isArray) {
                    for (item in root) {
                        lists.add(
                            mapOf(
                                "id" to item.path("id").asText(),
                                "title" to item.path("title").asText(),
                                "itemCount" to item.path("itemCount").asInt(0)
                            )
                        )
                    }
                }
                lists
            } else emptyList()
        } catch (e: Exception) {
            log.warn("Could not retrieve AH profile lists: {}", e.message)
            emptyList()
        }
    }

    fun buildHeaders(token: String?): HttpHeaders {
        val headers = HttpHeaders()
        headers.set("User-Agent", USER_AGENT)
        headers.set("X-Application", "AHWEBSHOP")
        headers.contentType = MediaType.APPLICATION_JSON
        if (!token.isNullOrBlank()) {
            headers.set("Authorization", "Bearer $token")
        }
        return headers
    }
}

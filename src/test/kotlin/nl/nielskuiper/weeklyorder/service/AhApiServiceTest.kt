package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.repository.AhCredentialRepository
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

class AhApiServiceTest {

    private lateinit var credentialRepository: AhCredentialRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var ahApiService: AhApiService

    @BeforeEach
    fun setUp() {
        credentialRepository = mock()
        productRepository = mock()
        ahApiService = AhApiService(credentialRepository, productRepository)

        whenever(productRepository.save(any<Product>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `getOAuthLoginUrl contains client_id and redirect_uri`() {
        val url = ahApiService.getOAuthLoginUrl()
        assertTrue(url.contains("client_id=appie"))
        assertTrue(url.contains("redirect_uri=appie://login-exit"))
    }

    @Test
    fun `buildHeaders configures required headers`() {
        val headers = ahApiService.buildHeaders("test-token-123")
        assertEquals("Appie/8.22.3 Android/12", headers.getFirst("User-Agent"))
        assertEquals("AHWEBSHOP", headers.getFirst("X-Application"))
        assertEquals("Bearer test-token-123", headers.getFirst("Authorization"))
    }

    @Test
    fun `parseProductSearchResults parses AH JSON with bonus details and images`() {
        val json = """
        {
          "products": [
            {
              "webshopId": 202811,
              "title": "AH Liefde & Passie Volkoren",
              "brand": "AH",
              "currentPrice": 2.69,
              "isBonus": false,
              "unitSize": "450 g",
              "images": [
                { "url": "https://static.ah.nl/image.jpg" }
              ]
            },
            {
              "webshopId": 489201,
              "title": "AH Zaans Volkoren",
              "brand": "AH",
              "priceBeforeBonus": 1.89,
              "currentPrice": 1.49,
              "isBonus": true,
              "bonusMechanism": "1+1 gratis",
              "unitSize": "800 g",
              "images": []
            }
          ]
        }
        """.trimIndent()

        val results = ahApiService.parseProductSearchResults(json)

        assertEquals(2, results.size)

        val p1 = results[0]
        assertEquals(202811L, p1.webshopId)
        assertEquals("AH Liefde & Passie Volkoren", p1.title)
        assertEquals(BigDecimal("2.69"), p1.price)
        assertFalse(p1.isBonus)
        assertEquals("https://static.ah.nl/image.jpg", p1.imageUrl)

        val p2 = results[1]
        assertEquals(489201L, p2.webshopId)
        assertEquals("AH Zaans Volkoren", p2.title)
        assertEquals(BigDecimal("1.89"), p2.price)
        assertEquals(BigDecimal("1.49"), p2.bonusPrice)
        assertTrue(p2.isBonus)
        assertEquals("1+1 gratis", p2.bonusMechanism)
    }
}

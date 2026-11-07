package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.model.StapleItem
import nl.nielskuiper.weeklyorder.model.StapleType
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.StapleItemRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate

class StapleServiceTest {

    private lateinit var stapleRepository: StapleItemRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var ahApiService: AhApiService
    private lateinit var stapleService: StapleService

    @BeforeEach
    fun setUp() {
        stapleRepository = mock()
        productRepository = mock()
        ahApiService = mock()
        stapleService = StapleService(stapleRepository, productRepository, ahApiService)
    }

    @Test
    fun `evaluateStaplesForWeek categorizes weekly, occasional due, and bonus pull-forward items`() {
        val targetTuesday = LocalDate.of(2026, 11, 3)

        // 1. Weekly staple: Melk
        val milk = StapleItem(
            name = "Melk",
            type = StapleType.WEEKLY,
            defaultProduct = Product(webshopId = 1L, title = "AH Melk", price = BigDecimal("1.25"))
        )

        // 2. Occasional item due: Toiletpapier (ordered 4 weeks ago, frequency is 3 weeks)
        val tp = StapleItem(
            name = "Toiletpapier",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 3,
            lastOrderedDate = targetTuesday.minusWeeks(4),
            defaultProduct = Product(webshopId = 2L, title = "Page Toiletpapier", price = BigDecimal("9.99"))
        )

        // 3. Occasional item NOT due, but on BONUS: Olijfolie (ordered 1 week ago, frequency is 4, but isBonus = true)
        val oil = StapleItem(
            name = "Olijfolie",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 4,
            lastOrderedDate = targetTuesday.minusWeeks(1),
            defaultProduct = Product(
                webshopId = 3L,
                title = "AH Olijfolie",
                price = BigDecimal("7.49"),
                bonusPrice = BigDecimal("5.61"),
                isBonus = true,
                bonusMechanism = "25% korting"
            )
        )

        // 4. Occasional item NOT due and NOT on bonus: Roomboter (ordered 1 week ago, frequency is 4)
        val butter = StapleItem(
            name = "Roomboter",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 4,
            lastOrderedDate = targetTuesday.minusWeeks(1),
            defaultProduct = Product(webshopId = 4L, title = "Campina Roomboter", price = BigDecimal("3.19"))
        )

        whenever(stapleRepository.findAll()).thenReturn(listOf(milk, tp, oil, butter))

        val result = stapleService.evaluateStaplesForWeek(targetTuesday)

        val weekly = result["weeklyItems"] as List<*>
        val due = result["occasionalDue"] as List<*>
        val pullForward = result["occasionalBonusPullForward"] as List<*>
        val notDue = result["occasionalNotDue"] as List<*>

        assertEquals(1, weekly.size, "Expected 1 weekly item")
        assertEquals(1, due.size, "Expected 1 occasional item due")
        assertEquals(1, pullForward.size, "Expected 1 occasional item on bonus pull-forward")
        assertEquals(1, notDue.size, "Expected 1 occasional item not due")
    }

    @Test
    fun `findBonusSwap identifies acceptable alternative on bonus`() {
        val regularBread = Product(webshopId = 100L, title = "AH Liefde & Passie Volkoren", price = BigDecimal("2.69"), isBonus = false)
        val bonusBread = Product(
            webshopId = 101L,
            title = "AH Zaans Volkoren",
            price = BigDecimal("1.89"),
            bonusPrice = BigDecimal("1.49"),
            isBonus = true,
            bonusMechanism = "1+1 gratis"
        )

        val staple = StapleItem(
            name = "Volkoren Brood",
            type = StapleType.WEEKLY,
            defaultProduct = regularBread,
            alternativeProducts = mutableListOf(bonusBread)
        )

        val swap = stapleService.findBonusSwap(staple)

        assertTrue(swap["hasSwap"] == true)
        val alt = swap["alternativeProduct"] as Product
        assertEquals(101L, alt.webshopId)
        assertEquals("AH Zaans Volkoren", alt.title)
    }

    @Test
    fun `findBonusSwap uses bonusSearchQuery when no pre-linked alternative is on bonus`() {
        val regularBread = Product(webshopId = 100L, title = "AH Liefde & Passie Volkoren", price = BigDecimal("2.69"), isBonus = false)
        val staple = StapleItem(
            name = "Volkoren Brood",
            type = StapleType.WEEKLY,
            defaultProduct = regularBread,
            bonusSearchQuery = "volkoren brood"
        )

        val catalogMatch = Product(
            webshopId = 102L,
            title = "AH Volkoren Tijgerbrood",
            price = BigDecimal("1.99"),
            bonusPrice = BigDecimal("1.49"),
            isBonus = true,
            bonusMechanism = "25% korting"
        )

        whenever(ahApiService.searchProducts("volkoren brood")).thenReturn(listOf(catalogMatch))

        val swap = stapleService.findBonusSwap(staple)

        assertTrue(swap["hasSwap"] == true)
        val alt = swap["alternativeProduct"] as Product
        assertEquals(102L, alt.webshopId)
        assertEquals("AH Volkoren Tijgerbrood", alt.title)
    }
}

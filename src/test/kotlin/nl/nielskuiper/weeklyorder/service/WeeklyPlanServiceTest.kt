package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.*
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.RecipeRepository
import nl.nielskuiper.weeklyorder.repository.StapleItemRepository
import nl.nielskuiper.weeklyorder.repository.WeeklyPlanRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

class WeeklyPlanServiceTest {

    private lateinit var planRepository: WeeklyPlanRepository
    private lateinit var recipeRepository: RecipeRepository
    private lateinit var stapleRepository: StapleItemRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var ahApiService: AhApiService
    private lateinit var planService: WeeklyPlanService

    @BeforeEach
    fun setUp() {
        planRepository = mock()
        recipeRepository = mock()
        stapleRepository = mock()
        productRepository = mock()
        ahApiService = mock()
        planService = WeeklyPlanService(planRepository, recipeRepository, stapleRepository, productRepository, ahApiService)

        whenever(planRepository.save(any<WeeklyPlan>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `getNextDeliveryTuesday always returns next Tuesday`() {
        // Monday 2026-11-02 -> Tuesday 2026-11-03
        val monday = LocalDate.of(2026, 11, 2)
        val target = planService.getNextDeliveryTuesday(monday)
        assertEquals(DayOfWeek.TUESDAY, target.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 3), target)

        // Wednesday 2026-11-04 -> Tuesday 2026-11-10
        val wednesday = LocalDate.of(2026, 11, 4)
        val nextTarget = planService.getNextDeliveryTuesday(wednesday)
        assertEquals(DayOfWeek.TUESDAY, nextTarget.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 10), nextTarget)
    }

    @Test
    fun `buildAggregatedPlan aggregates duplicate ingredients across recipes`() {
        val courgette = Product(webshopId = 504001L, title = "AH Courgette", price = BigDecimal("0.99"))

        // Recipe 1: 2 courgettes
        val r1 = Recipe(id = 1L, name = "Pasta Pesto")
        r1.addIngredient(Ingredient(name = "courgette", quantity = 2.0, matchedProduct = courgette))

        // Recipe 2: 1 courgette
        val r2 = Recipe(id = 2L, name = "Groenteschotel")
        r2.addIngredient(Ingredient(name = "courgette", quantity = 1.0, matchedProduct = courgette))

        val plan = WeeklyPlan(id = 10L, yearNumber = 2026, weekNumber = 45)

        whenever(planRepository.findById(10L)).thenReturn(Optional.of(plan))
        whenever(recipeRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(r1, r2))

        val updatedPlan = planService.buildAggregatedPlan(10L, listOf(1L, 2L), emptyList())

        assertEquals(1, updatedPlan.items.size)
        val item = updatedPlan.items[0]
        assertEquals(504001L, item.product?.webshopId)
        assertEquals(3, item.quantity, "Expected 2 + 1 = 3 courgettes")
        assertTrue(item.sourceName!!.contains("Pasta Pesto"))
        assertTrue(item.sourceName!!.contains("Groenteschotel"))
    }

    @Test
    fun `buildAggregatedPlan applies bonus swap correctly`() {
        val standardBread = Product(webshopId = 202811L, title = "AH Liefde & Passie Volkoren", price = BigDecimal("2.69"))
        val bonusBread = Product(
            webshopId = 489201L,
            title = "AH Zaans Volkoren heel",
            price = BigDecimal("1.89"),
            bonusPrice = BigDecimal("1.49"),
            isBonus = true,
            bonusMechanism = "1+1 gratis"
        )

        val staple = StapleItem(id = 1L, name = "Volkoren Brood", type = StapleType.WEEKLY, defaultProduct = standardBread)

        val plan = WeeklyPlan(id = 20L, yearNumber = 2026, weekNumber = 45)

        whenever(planRepository.findById(20L)).thenReturn(Optional.of(plan))
        whenever(recipeRepository.findAllById(emptyList())).thenReturn(emptyList())
        whenever(stapleRepository.findById(1L)).thenReturn(Optional.of(staple))
        whenever(productRepository.findById(489201L)).thenReturn(Optional.of(bonusBread))

        // Staple selection with chosenProductId = bonusBread
        val stapleSelections = listOf(
            mapOf<String, Any?>(
                "stapleId" to 1L,
                "quantity" to 2,
                "chosenProductId" to 489201L
            )
        )

        val updatedPlan = planService.buildAggregatedPlan(20L, emptyList(), stapleSelections)

        assertEquals(1, updatedPlan.items.size)
        val item = updatedPlan.items[0]
        assertEquals(489201L, item.product?.webshopId, "Should use the swapped bonus bread")
        assertTrue(item.isSwapped, "Should be flagged as swapped")
        assertEquals(202811L, item.originalProduct?.webshopId, "Original product should be standard bread")
        assertEquals(2, item.quantity)
    }

    @Test
    fun `pushPlanToAh updates occasional staple lastOrderedDate on success`() {
        val oilProduct = Product(webshopId = 301100L, title = "AH Olijfolie", price = BigDecimal("7.49"))
        val oilStaple = StapleItem(
            id = 5L,
            name = "Olijfolie",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 4,
            defaultProduct = oilProduct,
            lastOrderedDate = LocalDate.now().minusWeeks(4)
        )

        val plan = WeeklyPlan(id = 30L, yearNumber = 2026, weekNumber = 45)
        val planItem = PlanItem(
            product = oilProduct,
            quantity = 1,
            sourceType = "OCCASIONAL",
            sourceName = "Olijfolie"
        )
        plan.addItem(planItem)

        whenever(planRepository.findById(30L)).thenReturn(Optional.of(plan))
        whenever(stapleRepository.findByType(StapleType.OCCASIONAL)).thenReturn(listOf(oilStaple))
        whenever(ahApiService.pushItemsToCart(any())).thenReturn(
            mapOf("success" to true, "target" to "BESTELLING", "orderId" to "ORDER-1234")
        )

        val result = planService.pushPlanToAh(30L)

        assertTrue(result["success"] == true)
        assertEquals("ORDERED", plan.status)
        assertEquals("ORDER-1234", plan.ahOrderId)
        assertNotNull(plan.orderedAt)

        // Verify that the occasional staple's lastOrderedDate was updated to today
        assertEquals(LocalDate.now(), oilStaple.lastOrderedDate)
        verify(stapleRepository).save(oilStaple)
    }
}

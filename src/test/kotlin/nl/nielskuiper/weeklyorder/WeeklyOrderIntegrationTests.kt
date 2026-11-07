package nl.nielskuiper.weeklyorder

import nl.nielskuiper.weeklyorder.service.RecipeService
import nl.nielskuiper.weeklyorder.service.StapleService
import nl.nielskuiper.weeklyorder.service.WeeklyPlanService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class WeeklyOrderIntegrationTests {

    @Autowired
    private lateinit var recipeService: RecipeService

    @Autowired
    private lateinit var stapleService: StapleService

    @Autowired
    private lateinit var weeklyPlanService: WeeklyPlanService

    @Test
    fun `context loads and sample data is initialized`() {
        val recipes = recipeService.getAllRecipes()
        assertFalse(recipes.isEmpty(), "Recipes should be pre-seeded")

        val staples = stapleService.getAllStaples()
        assertFalse(staples.isEmpty(), "Staples should be pre-seeded")
    }

    @Test
    fun `full end-to-end weekly planning workflow operates correctly`() {
        // 1. Get upcoming plan for Tuesday
        val plan = weeklyPlanService.getOrCreateUpcomingPlan()
        assertNotNull(plan.id)
        assertNotNull(plan.targetDeliveryDate)

        // 2. Select 2 seeded recipes: AVG and Quesadillas
        val allRecipes = recipeService.getAllRecipes()
        assertTrue(allRecipes.size >= 2)
        val selectedRecipeIds = listOf(allRecipes[0].id!!, allRecipes[1].id!!)

        // 3. Select 1 staple with swap applied
        val allStaples = stapleService.getAllStaples()
        val breadStaple = allStaples.first { it.name == "Volkoren Brood" }

        val stapleSelections = listOf(
            mapOf<String, Any?>(
                "stapleId" to breadStaple.id,
                "quantity" to 1,
                "chosenProductId" to breadStaple.alternativeProducts.firstOrNull()?.webshopId
            )
        )

        // 4. Build aggregated plan
        val builtPlan = weeklyPlanService.buildAggregatedPlan(plan.id!!, selectedRecipeIds, stapleSelections)

        assertFalse(builtPlan.items.isEmpty())
        assertTrue(builtPlan.totalEstimatedCost > java.math.BigDecimal.ZERO)

        // 5. Verify the staple item was swapped
        val swappedItem = builtPlan.items.find { it.isSwapped }
        assertNotNull(swappedItem, "Expected at least one item to be marked as swapped with bonus bread")
    }
}

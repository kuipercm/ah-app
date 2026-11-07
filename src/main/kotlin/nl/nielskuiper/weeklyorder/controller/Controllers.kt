package nl.nielskuiper.weeklyorder.controller

import nl.nielskuiper.weeklyorder.model.Ingredient
import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.model.Recipe
import nl.nielskuiper.weeklyorder.model.StapleItem
import nl.nielskuiper.weeklyorder.model.WeeklyPlan
import nl.nielskuiper.weeklyorder.service.AhApiService
import nl.nielskuiper.weeklyorder.service.RecipeService
import nl.nielskuiper.weeklyorder.service.StapleService
import nl.nielskuiper.weeklyorder.service.WeeklyPlanService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/recipes")
class RecipeController(private val recipeService: RecipeService) {

    @GetMapping
    fun getAllRecipes(): List<Map<String, Any?>> {
        val recipes = recipeService.getAllRecipes()
        return recipes.map { r ->
            val bonusDetails = recipeService.getRecipeBonusDetails(r)
            mapOf(
                "id" to r.id,
                "name" to r.name,
                "notes" to r.notes,
                "defaultServings" to r.defaultServings,
                "ingredients" to r.ingredients,
                "bonusCount" to bonusDetails["bonusCount"],
                "bonusItems" to bonusDetails["bonusItems"],
                "createdAt" to r.createdAt
            )
        }
    }

    @GetMapping("/{id}")
    fun getRecipeById(@PathVariable id: Long): ResponseEntity<Recipe> {
        val r = recipeService.getRecipeById(id)
        return if (r != null) ResponseEntity.ok(r) else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun saveRecipe(@RequestBody recipe: Recipe): Recipe = recipeService.saveRecipe(recipe)

    @DeleteMapping("/{id}")
    fun deleteRecipe(@PathVariable id: Long): ResponseEntity<Void> {
        recipeService.deleteRecipe(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/bulk-parse")
    fun bulkParse(@RequestBody payload: Map<String, String>): List<Recipe> {
        val rawText = payload["text"]
        val autoMatch = payload.getOrDefault("autoMatch", "true").toBoolean()
        return recipeService.parseAndSaveBulkRecipes(rawText, autoMatch)
    }

    @PostMapping("/ingredients/{ingredientId}/link")
    fun linkProduct(
        @PathVariable ingredientId: Long,
        @RequestBody payload: Map<String, Long>
    ): Ingredient {
        val webshopId = payload["webshopId"] ?: throw IllegalArgumentException("webshopId is required")
        return recipeService.linkProductToIngredient(ingredientId, webshopId)
    }
}

@RestController
@RequestMapping("/api/staples")
class StapleController(
    private val stapleService: StapleService,
    private val weeklyPlanService: WeeklyPlanService
) {

    @GetMapping
    fun getAllStaples(): List<StapleItem> = stapleService.getAllStaples()

    @GetMapping("/evaluated")
    fun getEvaluatedStaples(): Map<String, Any> {
        val targetTuesday = weeklyPlanService.getNextDeliveryTuesday()
        return stapleService.evaluateStaplesForWeek(targetTuesday)
    }

    @PostMapping
    fun saveStaple(@RequestBody staple: StapleItem): StapleItem = stapleService.saveStaple(staple)

    @DeleteMapping("/{id}")
    fun deleteStaple(@PathVariable id: Long): ResponseEntity<Void> {
        stapleService.deleteStaple(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/alternative")
    fun addAlternative(
        @PathVariable id: Long,
        @RequestBody payload: Map<String, Long>
    ): StapleItem {
        val webshopId = payload["webshopId"] ?: throw IllegalArgumentException("webshopId is required")
        return stapleService.addAlternativeProduct(id, webshopId)
    }
}

@RestController
@RequestMapping("/api/planner")
class WeeklyPlanController(private val planService: WeeklyPlanService) {

    @GetMapping("/upcoming")
    fun getUpcomingPlan(): WeeklyPlan = planService.getOrCreateUpcomingPlan()

    @PostMapping("/{planId}/build")
    fun buildPlan(
        @PathVariable planId: Long,
        @RequestBody payload: Map<String, Any?>
    ): WeeklyPlan {
        val recipeIds = (payload["recipeIds"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toLong() } ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val stapleSelections = payload["staples"] as? List<Map<String, Any?>>

        return planService.buildAggregatedPlan(planId, recipeIds, stapleSelections)
    }

    @PostMapping("/{planId}/push-ah")
    fun pushToAh(@PathVariable planId: Long): ResponseEntity<Map<String, Any?>> {
        val result = planService.pushPlanToAh(planId)
        return ResponseEntity.ok(result)
    }
}

@RestController
@RequestMapping("/api/ah")
class AhController(private val ahApiService: AhApiService) {

    @GetMapping("/auth/status")
    fun getAuthStatus(): Map<String, Any?> = ahApiService.getAuthStatus()

    @PostMapping("/auth/exchange")
    fun exchangeAuthCode(@RequestBody payload: Map<String, String>): ResponseEntity<Map<String, Any>> {
        val code = payload["code"] ?: ""
        val success = ahApiService.exchangeAuthCode(code)
        return ResponseEntity.ok(mapOf("success" to success))
    }

    @PostMapping("/auth/logout")
    fun logout(): ResponseEntity<Map<String, Any>> {
        ahApiService.logout()
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/search")
    fun search(@RequestParam query: String): List<Product> = ahApiService.searchProducts(query)

    @GetMapping("/active-order")
    fun getActiveOrder(): Map<String, Any?> = ahApiService.getActiveOrder()

    @GetMapping("/lists")
    fun getUserLists(): List<Map<String, Any>> = ahApiService.getUserLists()
}

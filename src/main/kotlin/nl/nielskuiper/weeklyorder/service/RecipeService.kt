package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.Ingredient
import nl.nielskuiper.weeklyorder.model.Recipe
import nl.nielskuiper.weeklyorder.repository.IngredientRepository
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.RecipeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.regex.Pattern

@Service
@Transactional
class RecipeService(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val productRepository: ProductRepository,
    private val ahApiService: AhApiService
) {
    private val log = LoggerFactory.getLogger(RecipeService::class.java)

    fun getAllRecipes(): List<Recipe> = recipeRepository.findAll()

    fun getRecipeById(id: Long): Recipe? = recipeRepository.findById(id).orElse(null)

    fun saveRecipe(recipe: Recipe): Recipe = recipeRepository.save(recipe)

    fun deleteRecipe(id: Long) {
        recipeRepository.deleteById(id)
    }

    fun linkProductToIngredient(ingredientId: Long, webshopId: Long): Ingredient {
        val ingredient = ingredientRepository.findById(ingredientId)
            .orElseThrow { IllegalArgumentException("Ingredient not found: $ingredientId") }

        val product = productRepository.findById(webshopId)
            .orElseThrow { IllegalArgumentException("Product not found: $webshopId") }

        ingredient.matchedProduct = product
        return ingredientRepository.save(ingredient)
    }

    /**
     * Parses recipes in format:
     * Recipe Name (ingredient1, ingredient2, ...) [-- optional notes]
     */
    fun parseAndSaveBulkRecipes(rawText: String?, autoMatchAh: Boolean = true): List<Recipe> {
        if (rawText.isNullOrBlank()) return emptyList()

        val savedRecipes = mutableListOf<Recipe>()
        val lines = rawText.lines()
        val pattern = Pattern.compile("^\\s*([^(]+?)\\s*\\(([^)]+)\\)(?:\\s*(?:--|-)\\s*(.*))?$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                val title = matcher.group(1).trim()
                val ingredientsPart = matcher.group(2).trim()
                val notes = matcher.group(3)?.trim()

                val recipe = Recipe(name = title, notes = notes)

                val items = ingredientsPart.split("[,;]".toRegex())
                for (itemStr in items) {
                    val cleanItem = itemStr.trim()
                    if (cleanItem.isNotEmpty()) {
                        val ingredient = parseIngredientString(cleanItem)
                        recipe.addIngredient(ingredient)
                    }
                }

                val saved = recipeRepository.save(recipe)
                if (autoMatchAh) {
                    autoMatchIngredients(saved)
                }
                savedRecipes.add(saved)
            } else {
                val simple = Recipe(name = trimmed)
                savedRecipes.add(recipeRepository.save(simple))
            }
        }

        log.info("Parsed and saved {} recipes from bulk text", savedRecipes.size)
        return savedRecipes
    }

    fun parseIngredientString(str: String): Ingredient {
        val qtyPattern = Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Z]+)?\\s+(.*)$")
        val m = qtyPattern.matcher(str)
        if (m.matches()) {
            try {
                val qty = m.group(1).replace(",", ".").toDouble()
                val unit = m.group(2)?.trim()
                val name = m.group(3).trim()
                return Ingredient(name = name, quantity = qty, unit = unit)
            } catch (ignored: Exception) {}
        }
        return Ingredient(name = str, quantity = 1.0)
    }

    fun autoMatchIngredients(recipe: Recipe) {
        for (ingredient in recipe.ingredients) {
            if (ingredient.matchedProduct == null) {
                val local = productRepository.findByTitleContainingIgnoreCase(ingredient.name)
                if (local.isNotEmpty()) {
                    ingredient.matchedProduct = local[0]
                } else {
                    val online = ahApiService.searchProducts(ingredient.name)
                    if (online.isNotEmpty()) {
                        ingredient.matchedProduct = online[0]
                    }
                }
            }
        }
        recipeRepository.save(recipe)
    }

    @Transactional(readOnly = true)
    fun getRecipeBonusDetails(recipe: Recipe): Map<String, Any> {
        var bonusCount = 0
        val bonusItemNames = mutableListOf<String>()

        for (ing in recipe.ingredients) {
            val p = ing.matchedProduct
            if (p != null && p.isBonus) {
                bonusCount++
                bonusItemNames.add("${p.title} (${p.bonusMechanism ?: "Bonus"})")
            }
        }

        return mapOf(
            "recipeId" to (recipe.id ?: 0L),
            "bonusCount" to bonusCount,
            "bonusItems" to bonusItemNames
        )
    }
}

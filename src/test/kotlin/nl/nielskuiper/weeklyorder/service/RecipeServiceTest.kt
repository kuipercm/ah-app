package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.Ingredient
import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.model.Recipe
import nl.nielskuiper.weeklyorder.repository.IngredientRepository
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.RecipeRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

class RecipeServiceTest {

    private lateinit var recipeRepository: RecipeRepository
    private lateinit var ingredientRepository: IngredientRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var ahApiService: AhApiService
    private lateinit var recipeService: RecipeService

    @BeforeEach
    fun setUp() {
        recipeRepository = mock()
        ingredientRepository = mock()
        productRepository = mock()
        ahApiService = mock()
        recipeService = RecipeService(recipeRepository, ingredientRepository, productRepository, ahApiService)

        whenever(recipeRepository.save(any<Recipe>())).thenAnswer { it.arguments[0] }
        whenever(ingredientRepository.save(any<Ingredient>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `parseAndSaveBulkRecipes correctly parses user recipe format with notes`() {
        val input = """
            AVG (spruiten, aardappels, spekjes, appel) -- Diepvries: Speklap bbq, geen idee of die nog goed is, maar proberen
            Flespompoen tajine pagina 97 (Flespompoen, knoflook, verse rozemarijn, couscous)
            Quesadillas met bloemkool (bloemkool, Wraps, kaas, ui, paprika, champignons)
        """.trimIndent()

        val parsed = recipeService.parseAndSaveBulkRecipes(input, false)

        assertEquals(3, parsed.size)

        // Recipe 1: with notes
        val r1 = parsed[0]
        assertEquals("AVG", r1.name)
        assertEquals("Diepvries: Speklap bbq, geen idee of die nog goed is, maar proberen", r1.notes)
        assertEquals(4, r1.ingredients.size)
        assertEquals("spruiten", r1.ingredients[0].name)
        assertEquals("aardappels", r1.ingredients[1].name)
        assertEquals("spekjes", r1.ingredients[2].name)
        assertEquals("appel", r1.ingredients[3].name)

        // Recipe 2: with space in name and no notes
        val r2 = parsed[1]
        assertEquals("Flespompoen tajine pagina 97", r2.name)
        assertNull(r2.notes)
        assertEquals(4, r2.ingredients.size)

        // Recipe 3
        val r3 = parsed[2]
        assertEquals("Quesadillas met bloemkool", r3.name)
        assertEquals(6, r3.ingredients.size)
    }

    @Test
    fun `parseIngredientString extracts quantity, unit and name correctly`() {
        val ing1 = recipeService.parseIngredientString("500 g aardappels")
        assertEquals(500.0, ing1.quantity)
        assertEquals("g", ing1.unit)
        assertEquals("aardappels", ing1.name)

        val ing2 = recipeService.parseIngredientString("2 stuks courgette")
        assertEquals(2.0, ing2.quantity)
        assertEquals("stuks", ing2.unit)
        assertEquals("courgette", ing2.name)

        val ing3 = recipeService.parseIngredientString("bloemkool")
        assertEquals(1.0, ing3.quantity)
        assertNull(ing3.unit)
        assertEquals("bloemkool", ing3.name)
    }

    @Test
    fun `linkProductToIngredient successfully binds product`() {
        val ingredient = Ingredient(id = 1L, name = "bloemkool")
        val product = Product(webshopId = 503001L, title = "AH Bloemkool", price = BigDecimal("2.29"))

        whenever(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient))
        whenever(productRepository.findById(503001L)).thenReturn(Optional.of(product))

        val result = recipeService.linkProductToIngredient(1L, 503001L)

        assertNotNull(result.matchedProduct)
        assertEquals("AH Bloemkool", result.matchedProduct?.title)
        verify(ingredientRepository).save(ingredient)
    }

    @Test
    fun `getRecipeBonusDetails correctly calculates bonus count`() {
        val bonusProduct = Product(
            webshopId = 1L,
            title = "AH Bloemkool",
            price = BigDecimal("2.29"),
            bonusPrice = BigDecimal("1.49"),
            isBonus = true,
            bonusMechanism = "1+1 gratis"
        )
        val regularProduct = Product(
            webshopId = 2L,
            title = "AH Wraps",
            price = BigDecimal("1.89"),
            isBonus = false
        )

        val recipe = Recipe(name = "Quesadilla")
        recipe.addIngredient(Ingredient(name = "bloemkool", matchedProduct = bonusProduct))
        recipe.addIngredient(Ingredient(name = "wraps", matchedProduct = regularProduct))

        val details = recipeService.getRecipeBonusDetails(recipe)

        assertEquals(1, details["bonusCount"])
        val items = details["bonusItems"] as List<*>
        assertEquals(1, items.size)
        assertTrue((items[0] as String).contains("1+1 gratis"))
    }
}

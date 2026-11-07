package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.*
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.RecipeRepository
import nl.nielskuiper.weeklyorder.repository.StapleItemRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class DataInitializerService(
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
    private val stapleRepository: StapleItemRepository,
    private val recipeService: RecipeService
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataInitializerService::class.java)

    override fun run(vararg args: String?) {
        if (recipeRepository.count() == 0L && stapleRepository.count() == 0L) {
            log.info("Initializing sample data with user recipes, staples, and AH catalog items...")
            initSampleProductsAndStaples()
            initSampleRecipes()
        }
    }

    private fun initSampleProductsAndStaples() {
        val breadPassie = saveProduct(202811L, "AH Liefde & Passie Volkoren", "AH", 2.69, null, false, null, "450 g", "https://static.ah.nl/static/product/AHI_43545239383631313437_1_200x200_JPG.JPG")
        val breadZaans = saveProduct(489201L, "AH Zaans Volkoren heel", "AH", 1.89, 1.49, true, "1+1 gratis", "800 g", "https://static.ah.nl/static/product/AHI_43545239383538313233_1_200x200_JPG.JPG")
        val milk = saveProduct(101234L, "AH Halfvolle melk", "AH", 1.25, null, false, null, "1 liter", null)
        val eggs = saveProduct(101235L, "AH Vrije-uitloop eieren M", "AH", 2.99, null, false, null, "10 stuks", null)
        val bananas = saveProduct(101236L, "AH Bananen", "AH", 1.99, null, false, null, "1 kg (ca. 5-6 stuks)", null)

        val oliveOil = saveProduct(301100L, "AH Olijfolie extra vierge", "AH", 7.49, 5.61, true, "25% korting", "500 ml", null)
        val toiletPaper = saveProduct(301101L, "Page Compleet Schoon Toiletpapier", "Page", 9.99, null, false, null, "16 rollen", null)
        val butter = saveProduct(301102L, "Campina Roomboter ongezouten", "Campina", 3.19, null, false, null, "250 g", null)
        val dishTabs = saveProduct(301103L, "Finish Power All in 1 Vaatwastabletten", "Finish", 14.99, 7.49, true, "1+1 gratis", "50 stuks", null)

        // Recipe items
        saveProduct(501001L, "AH Verse spruiten", "AH", 1.49, null, false, null, "500 g", null)
        saveProduct(501002L, "AH Kruimige aardappelen", "AH", 2.29, null, false, null, "1.5 kg", null)
        saveProduct(501003L, "AH Gerookte spekblokjes", "AH", 2.79, null, false, null, "250 g", null)
        saveProduct(501004L, "AH Elstar appels", "AH", 2.19, null, false, null, "1 kg", null)

        saveProduct(502001L, "AH Flespompoen", "AH", 1.79, null, false, null, "1 stuk", null)
        saveProduct(502002L, "AH Verse knoflook", "AH", 1.09, null, false, null, "1 stuk", null)
        saveProduct(502003L, "AH Rozemarijn", "AH", 1.39, null, false, null, "15 g", null)
        saveProduct(502004L, "AH Couscous medium", "AH", 1.45, null, false, null, "500 g", null)

        saveProduct(503001L, "AH Bloemkool", "AH", 2.29, 1.49, true, "Bonus: €1.49", "1 stuk", null)
        saveProduct(503002L, "Santa Maria Tortilla wraps original", "Santa Maria", 2.39, 1.79, true, "2e halve prijs", "8 stuks", null)
        saveProduct(503003L, "AH Jong belegen geraspte kaas", "AH", 2.49, null, false, null, "200 g", null)
        saveProduct(503004L, "AH Kastanjechampignons", "AH", 1.79, null, false, null, "250 g", null)

        saveProduct(504001L, "AH Courgette", "AH", 0.99, null, false, null, "1 stuk", null)
        saveProduct(504002L, "AH Cherrytomaten", "AH", 1.39, null, false, null, "250 g", null)
        saveProduct(504003L, "Grand'Italia Penne rigate", "Grand'Italia", 2.09, 1.56, true, "25% korting", "500 g", null)
        saveProduct(504004L, "AH Groene pesto alla Genovese", "AH", 1.79, null, false, null, "190 g", null)
        saveProduct(504005L, "Galbani Mozzarella", "Galbani", 1.69, null, false, null, "125 g", null)

        // Weekly Staples
        val bread = StapleItem(
            name = "Volkoren Brood",
            type = StapleType.WEEKLY,
            frequencyWeeks = 1,
            defaultProduct = breadPassie,
            alternativeProducts = mutableListOf(breadZaans),
            bonusSearchQuery = "volkoren brood",
            category = "Bakkerij"
        )
        stapleRepository.save(bread)

        val milkStaple = StapleItem(
            name = "Halfvolle Melk",
            type = StapleType.WEEKLY,
            frequencyWeeks = 1,
            defaultProduct = milk,
            category = "Zuivel"
        )
        stapleRepository.save(milkStaple)

        val eggsStaple = StapleItem(
            name = "Eieren",
            type = StapleType.WEEKLY,
            frequencyWeeks = 1,
            defaultProduct = eggs,
            category = "Zuivel & Eieren"
        )
        stapleRepository.save(eggsStaple)

        val bananasStaple = StapleItem(
            name = "Bananen",
            type = StapleType.WEEKLY,
            frequencyWeeks = 1,
            defaultProduct = bananas,
            category = "Fruit"
        )
        stapleRepository.save(bananasStaple)

        // Occasionals
        val oil = StapleItem(
            name = "Olijfolie Extra Vierge",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 4,
            defaultProduct = oliveOil,
            bonusSearchQuery = "olijfolie extra vierge",
            lastOrderedDate = LocalDate.now().minusWeeks(2),
            category = "Voorraad"
        )
        stapleRepository.save(oil)

        val tp = StapleItem(
            name = "Toiletpapier",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 3,
            defaultProduct = toiletPaper,
            bonusSearchQuery = "toiletpapier",
            lastOrderedDate = LocalDate.now().minusWeeks(4),
            category = "Huishouden"
        )
        stapleRepository.save(tp)

        val btr = StapleItem(
            name = "Roomboter",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 2,
            defaultProduct = butter,
            lastOrderedDate = LocalDate.now().minusWeeks(2),
            category = "Zuivel"
        )
        stapleRepository.save(btr)

        val tabs = StapleItem(
            name = "Vaatwastabletten",
            type = StapleType.OCCASIONAL,
            frequencyWeeks = 4,
            defaultProduct = dishTabs,
            bonusSearchQuery = "vaatwastabletten",
            lastOrderedDate = LocalDate.now().minusWeeks(1),
            category = "Huishouden"
        )
        stapleRepository.save(tabs)
    }

    private fun initSampleRecipes() {
        val sampleText = """
            AVG (spruiten, aardappels, spekjes, appel) -- Diepvries: Speklap bbq, geen idee of die nog goed is, maar proberen
            Flespompoen tajine pagina 97 (Flespompoen, knoflook, verse rozemarijn, geschaafde amadel, stemgember, gedroogde abrikozen, honing, couscous)
            Quesadillas met bloemkool (bloemkool, Wraps, kaas, ui, paprika, champignons)
            Pasta pesto (courgette, cherry tomaat, pasta, pesto, paprika, ui, knoflook, mozzarella, avocado, strooikaas, pijnboompitten)
        """.trimIndent()

        recipeService.parseAndSaveBulkRecipes(sampleText, true)
        log.info("Initialized sample recipes from user's format")
    }

    private fun saveProduct(
        id: Long,
        title: String,
        brand: String?,
        price: Double,
        bonusPrice: Double?,
        isBonus: Boolean,
        bonusMechanism: String?,
        unitSize: String?,
        imageUrl: String?
    ): Product {
        val p = Product(
            webshopId = id,
            title = title,
            brand = brand,
            price = BigDecimal.valueOf(price),
            bonusPrice = bonusPrice?.let { BigDecimal.valueOf(it) },
            isBonus = isBonus,
            bonusMechanism = bonusMechanism,
            unitSize = unitSize,
            imageUrl = imageUrl
        )
        return productRepository.save(p)
    }
}

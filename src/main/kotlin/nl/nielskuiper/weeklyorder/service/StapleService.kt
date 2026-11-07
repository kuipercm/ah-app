package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.Product
import nl.nielskuiper.weeklyorder.model.StapleItem
import nl.nielskuiper.weeklyorder.model.StapleType
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.StapleItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class StapleService(
    private val stapleRepository: StapleItemRepository,
    private val productRepository: ProductRepository,
    private val ahApiService: AhApiService
) {
    fun getAllStaples(): List<StapleItem> = stapleRepository.findAll()

    fun getWeeklyStaples(): List<StapleItem> = stapleRepository.findByType(StapleType.WEEKLY)

    fun getOccasionalStaples(): List<StapleItem> = stapleRepository.findByType(StapleType.OCCASIONAL)

    fun saveStaple(item: StapleItem): StapleItem = stapleRepository.save(item)

    fun deleteStaple(id: Long) {
        stapleRepository.deleteById(id)
    }

    fun addAlternativeProduct(stapleId: Long, productWebshopId: Long): StapleItem {
        val staple = stapleRepository.findById(stapleId)
            .orElseThrow { IllegalArgumentException("Staple not found: $stapleId") }

        val product = productRepository.findById(productWebshopId)
            .orElseThrow { IllegalArgumentException("Product not found: $productWebshopId") }

        if (!staple.alternativeProducts.contains(product)) {
            staple.alternativeProducts.add(product)
        }
        return stapleRepository.save(staple)
    }

    @Transactional(readOnly = true)
    fun evaluateStaplesForWeek(targetDate: LocalDate): Map<String, Any> {
        val all = stapleRepository.findAll()

        val weeklyItems = mutableListOf<Map<String, Any?>>()
        val occasionalDue = mutableListOf<Map<String, Any?>>()
        val occasionalBonusPullForward = mutableListOf<Map<String, Any?>>()
        val occasionalNotDue = mutableListOf<Map<String, Any?>>()

        for (staple in all) {
            val dto = stapleToDto(staple, targetDate)

            if (staple.type == StapleType.WEEKLY) {
                weeklyItems.add(dto)
            } else {
                val isDue = staple.isDueThisWeek(targetDate)
                val onBonus = dto["isOnBonus"] == true

                if (isDue) {
                    occasionalDue.add(dto)
                } else if (onBonus) {
                    occasionalBonusPullForward.add(dto)
                } else {
                    occasionalNotDue.add(dto)
                }
            }
        }

        return mapOf(
            "weeklyItems" to weeklyItems,
            "occasionalDue" to occasionalDue,
            "occasionalBonusPullForward" to occasionalBonusPullForward,
            "occasionalNotDue" to occasionalNotDue
        )
    }

    fun stapleToDto(staple: StapleItem, targetDate: LocalDate): Map<String, Any?> {
        val defaultProduct = staple.defaultProduct
        val defaultOnBonus = defaultProduct?.isBonus == true
        val bonusSwap = findBonusSwap(staple)

        return mapOf(
            "id" to staple.id,
            "name" to staple.name,
            "type" to staple.type.name,
            "frequencyWeeks" to staple.frequencyWeeks,
            "defaultQuantity" to staple.defaultQuantity,
            "lastOrderedDate" to staple.lastOrderedDate,
            "isDue" to staple.isDueThisWeek(targetDate),
            "bonusSearchQuery" to staple.bonusSearchQuery,
            "defaultProduct" to defaultProduct,
            "isOnBonus" to defaultOnBonus,
            "bonusSwap" to bonusSwap
        )
    }

    /**
     * Finds acceptable alternatives on Bonus (e.g. volkoren brood -> other volkoren brood in bonus).
     */
    fun findBonusSwap(staple: StapleItem): Map<String, Any?> {
        // 1. Check explicitly linked alternatives
        for (alt in staple.alternativeProducts) {
            if (alt.isBonus) {
                return mapOf(
                    "hasSwap" to true,
                    "alternativeProduct" to alt,
                    "reason" to "${alt.title} is in bonus (${alt.bonusMechanism ?: "Bonus"})!"
                )
            }
        }

        // 2. Query AH with strict search query (e.g. "volkoren brood")
        val query = staple.bonusSearchQuery
        if (!query.isNullOrBlank()) {
            val matches = ahApiService.searchProducts(query)
            for (p in matches) {
                if (p.isBonus && (staple.defaultProduct == null || p.webshopId != staple.defaultProduct!!.webshopId)) {
                    return mapOf(
                        "hasSwap" to true,
                        "alternativeProduct" to p,
                        "reason" to "${p.title} is in bonus (${p.bonusMechanism ?: "Bonus"})!"
                    )
                }
            }
        }

        return mapOf("hasSwap" to false)
    }
}

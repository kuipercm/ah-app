package nl.nielskuiper.weeklyorder.service

import nl.nielskuiper.weeklyorder.model.*
import nl.nielskuiper.weeklyorder.repository.ProductRepository
import nl.nielskuiper.weeklyorder.repository.RecipeRepository
import nl.nielskuiper.weeklyorder.repository.StapleItemRepository
import nl.nielskuiper.weeklyorder.repository.WeeklyPlanRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.ceil

@Service
@Transactional
class WeeklyPlanService(
    private val planRepository: WeeklyPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val stapleRepository: StapleItemRepository,
    private val productRepository: ProductRepository,
    private val ahApiService: AhApiService
) {
    private val log = LoggerFactory.getLogger(WeeklyPlanService::class.java)

    fun getNextDeliveryTuesday(referenceDate: LocalDate = LocalDate.now()): LocalDate {
        return if (referenceDate.dayOfWeek == DayOfWeek.TUESDAY) {
            referenceDate
        } else {
            referenceDate.with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
        }
    }

    fun getOrCreateUpcomingPlan(): WeeklyPlan {
        val targetTuesday = getNextDeliveryTuesday()
        val weekFields = WeekFields.ISO
        val weekNumber = targetTuesday.get(weekFields.weekOfWeekBasedYear())
        val year = targetTuesday.get(weekFields.weekBasedYear())

        return planRepository.findByYearNumberAndWeekNumber(year, weekNumber)
            .orElseGet {
                val newPlan = WeeklyPlan(
                    yearNumber = year,
                    weekNumber = weekNumber,
                    targetDeliveryDate = targetTuesday
                )
                planRepository.save(newPlan)
            }
    }

    fun setPlanRecipes(planId: Long, recipeIds: List<Long>): WeeklyPlan {
        val plan = planRepository.findById(planId)
            .orElseThrow { IllegalArgumentException("Plan not found: $planId") }

        val recipes = recipeRepository.findAllById(recipeIds)
        plan.selectedRecipes = recipes
        return planRepository.save(plan)
    }

    fun buildAggregatedPlan(
        planId: Long,
        recipeIds: List<Long>?,
        stapleSelections: List<Map<String, Any?>>?
    ): WeeklyPlan {
        val plan = planRepository.findById(planId)
            .orElseThrow { IllegalArgumentException("Plan not found: $planId") }

        val recipes = recipeRepository.findAllById(recipeIds ?: emptyList())
        plan.selectedRecipes = recipes

        val aggregatedMap = linkedMapOf<Long, PlanItem>()

        // 1. Process Recipe Ingredients
        for (recipe in recipes) {
            for (ing in recipe.ingredients) {
                val product = ing.matchedProduct
                if (product != null) {
                    val pid = product.webshopId
                    val qty = ceil(ing.quantity).toInt()
                    val existing = aggregatedMap[pid]
                    if (existing != null) {
                        existing.quantity += qty
                        existing.sourceName = "${existing.sourceName}, ${recipe.name}"
                    } else {
                        val item = PlanItem(
                            product = product,
                            quantity = qty,
                            sourceType = "RECIPE",
                            sourceName = recipe.name,
                            isBonus = product.isBonus,
                            bonusMechanism = product.bonusMechanism
                        )
                        aggregatedMap[pid] = item
                    }
                }
            }
        }

        // 2. Process Selected Staples (Weekly + Occasionals)
        if (stapleSelections != null) {
            for (stapleSel in stapleSelections) {
                val stapleId = (stapleSel["stapleId"] as Number).toLong()
                val qty = (stapleSel["quantity"] as? Number)?.toInt() ?: 1
                val chosenProductId = (stapleSel["chosenProductId"] as? Number)?.toLong()

                val stapleOpt = stapleRepository.findById(stapleId)
                if (stapleOpt.isPresent) {
                    val staple = stapleOpt.get()
                    var productToUse = staple.defaultProduct
                    var isSwapped = false
                    val original = staple.defaultProduct

                    if (chosenProductId != null && (productToUse == null || chosenProductId != productToUse.webshopId)) {
                        val altProduct = productRepository.findById(chosenProductId)
                        if (altProduct.isPresent) {
                            productToUse = altProduct.get()
                            isSwapped = true
                        }
                    }

                    if (productToUse != null) {
                        val pid = productToUse.webshopId
                        val sourceType = if (staple.type == StapleType.WEEKLY) "WEEKLY_STAPLE" else "OCCASIONAL"
                        val existing = aggregatedMap[pid]
                        if (existing != null) {
                            existing.quantity += qty
                            existing.sourceName = "${existing.sourceName}, ${staple.name}"
                            if (existing.sourceType == "RECIPE") {
                                existing.sourceType = sourceType
                            }
                        } else {
                            val item = PlanItem(
                                product = productToUse,
                                quantity = qty,
                                sourceType = sourceType,
                                sourceName = staple.name,
                                isBonus = productToUse.isBonus,
                                bonusMechanism = productToUse.bonusMechanism,
                                originalProduct = if (isSwapped) original else null,
                                isSwapped = isSwapped
                            )
                            aggregatedMap[pid] = item
                        }
                    }
                }
            }
        }

        plan.items.clear()
        for (item in aggregatedMap.values) {
            plan.addItem(item)
        }
        return planRepository.save(plan)
    }

    fun pushPlanToAh(planId: Long): Map<String, Any?> {
        val plan = planRepository.findById(planId)
            .orElseThrow { IllegalArgumentException("Plan not found: $planId") }

        if (plan.items.isEmpty()) {
            return mapOf("success" to false, "message" to "De bestellijst is leeg")
        }

        val ahPayload = plan.items.mapNotNull { item ->
            item.product?.let {
                mapOf("productId" to it.webshopId, "quantity" to item.quantity)
            }
        }

        val ahResult = ahApiService.pushItemsToCart(ahPayload)

        if (ahResult["success"] == true) {
            plan.status = "ORDERED"
            plan.orderedAt = LocalDateTime.now()
            if (ahResult.containsKey("orderId")) {
                plan.ahOrderId = ahResult["orderId"] as? String
            }
            planRepository.save(plan)

            updateOccasionalLastOrderedDates(plan)
            log.info("Pushed {} items from plan {} to AH successfully", ahPayload.size, planId)
        }

        return ahResult
    }

    private fun updateOccasionalLastOrderedDates(plan: WeeklyPlan) {
        val today = LocalDate.now()
        val occasionals = stapleRepository.findByType(StapleType.OCCASIONAL)

        for (item in plan.items) {
            if (item.sourceType == "OCCASIONAL") {
                for (st in occasionals) {
                    val nameMatch = item.sourceName?.contains(st.name, ignoreCase = true) == true
                    val defaultProductId = st.defaultProduct?.webshopId
                    val productMatch = defaultProductId != null &&
                        (defaultProductId == item.product?.webshopId || defaultProductId == item.originalProduct?.webshopId)
                    if (nameMatch || productMatch) {
                        st.lastOrderedDate = today
                        stapleRepository.save(st)
                    }
                }
            }
        }
    }
}

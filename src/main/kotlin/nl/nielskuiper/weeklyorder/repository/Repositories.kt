package nl.nielskuiper.weeklyorder.repository

import nl.nielskuiper.weeklyorder.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, Long> {
    fun findByTitleContainingIgnoreCase(query: String): List<Product>
    fun findByIsBonusTrue(): List<Product>
}

@Repository
interface IngredientRepository : JpaRepository<Ingredient, Long> {
    fun findByMatchedProductIsNull(): List<Ingredient>
}

@Repository
interface RecipeRepository : JpaRepository<Recipe, Long> {
    fun findByNameContainingIgnoreCase(query: String): List<Recipe>
}

@Repository
interface StapleItemRepository : JpaRepository<StapleItem, Long> {
    fun findByType(type: StapleType): List<StapleItem>
}

@Repository
interface AhCredentialRepository : JpaRepository<AhCredential, Long>

@Repository
interface WeeklyPlanRepository : JpaRepository<WeeklyPlan, Long> {
    fun findByYearNumberAndWeekNumber(yearNumber: Int, weekNumber: Int): Optional<WeeklyPlan>
    fun findTopByOrderByYearNumberDescWeekNumberDesc(): Optional<WeeklyPlan>
}

@Repository
interface PlanItemRepository : JpaRepository<PlanItem, Long> {
    fun findByWeeklyPlanId(weeklyPlanId: Long): List<PlanItem>
}

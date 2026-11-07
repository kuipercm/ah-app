package nl.nielskuiper.weeklyorder.model

import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "weekly_plans")
class WeeklyPlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "year_number", nullable = false)
    var yearNumber: Int = 0,

    @Column(name = "week_number", nullable = false)
    var weekNumber: Int = 0,

    @Column(name = "target_delivery_date")
    var targetDeliveryDate: LocalDate? = null,

    @Column(nullable = false)
    var status: String = "DRAFT",

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "plan_recipes",
        joinColumns = [JoinColumn(name = "plan_id")],
        inverseJoinColumns = [JoinColumn(name = "recipe_id")]
    )
    var selectedRecipes: MutableList<Recipe> = mutableListOf(),

    @OneToMany(mappedBy = "weeklyPlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    var items: MutableList<PlanItem> = mutableListOf(),

    @Column(name = "ah_order_id")
    var ahOrderId: String? = null,

    @Column(name = "ordered_at")
    var orderedAt: LocalDateTime? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun addItem(item: PlanItem) {
        items.add(item)
        item.weeklyPlan = this
    }

    fun removeItem(item: PlanItem) {
        items.remove(item)
        item.weeklyPlan = null
    }

    val totalEstimatedCost: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.estimatedCost) }
}

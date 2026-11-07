package nl.nielskuiper.weeklyorder.model

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "plan_items")
class PlanItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_plan_id")
    @JsonBackReference
    var weeklyPlan: WeeklyPlan? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_webshop_id")
    var product: Product? = null,

    @Column(nullable = false)
    var quantity: Int = 1,

    @Column(name = "source_type")
    var sourceType: String? = null,

    @Column(name = "source_name")
    var sourceName: String? = null,

    @Column(name = "is_bonus")
    var isBonus: Boolean = false,

    @Column(name = "bonus_mechanism")
    var bonusMechanism: String? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "original_product_id")
    var originalProduct: Product? = null,

    @Column(name = "is_swapped")
    var isSwapped: Boolean = false
) {
    val estimatedCost: BigDecimal
        get() {
            val p = product ?: return BigDecimal.ZERO
            return p.effectivePrice.multiply(BigDecimal.valueOf(quantity.toLong()))
        }
}

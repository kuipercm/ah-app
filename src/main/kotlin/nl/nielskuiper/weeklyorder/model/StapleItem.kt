package nl.nielskuiper.weeklyorder.model

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "staple_items")
class StapleItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: StapleType = StapleType.WEEKLY,

    @Column(name = "frequency_weeks")
    var frequencyWeeks: Int = 1,

    @Column(name = "default_quantity")
    var defaultQuantity: Int = 1,

    @Column(name = "last_ordered_date")
    var lastOrderedDate: LocalDate? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_product_id")
    var defaultProduct: Product? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "staple_alternatives",
        joinColumns = [JoinColumn(name = "staple_id")],
        inverseJoinColumns = [JoinColumn(name = "product_webshop_id")]
    )
    var alternativeProducts: MutableList<Product> = mutableListOf(),

    @Column(name = "bonus_search_query")
    var bonusSearchQuery: String? = null,

    var category: String? = null
) {
    fun isDueThisWeek(targetDate: LocalDate): Boolean {
        if (type == StapleType.WEEKLY) {
            return true
        }
        val last = lastOrderedDate ?: return true
        val dueDate = last.plusWeeks(frequencyWeeks.toLong())
        return !targetDate.isBefore(dueDate)
    }
}

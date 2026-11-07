package nl.nielskuiper.weeklyorder.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "products")
class Product(
    @Id
    @Column(name = "webshop_id")
    var webshopId: Long = 0,

    @Column(nullable = false)
    var title: String = "",

    var brand: String? = null,

    @Column(precision = 10, scale = 2)
    var price: BigDecimal? = null,

    @Column(name = "bonus_price", precision = 10, scale = 2)
    var bonusPrice: BigDecimal? = null,

    @Column(name = "is_bonus")
    var isBonus: Boolean = false,

    @Column(name = "bonus_mechanism")
    var bonusMechanism: String? = null,

    @Column(name = "bonus_start_date")
    var bonusStartDate: LocalDate? = null,

    @Column(name = "bonus_end_date")
    var bonusEndDate: LocalDate? = null,

    @Column(name = "unit_size")
    var unitSize: String? = null,

    @Column(name = "image_url", length = 1000)
    var imageUrl: String? = null,

    var category: String? = null,

    @Column(name = "last_price_update")
    var lastPriceUpdate: LocalDate? = LocalDate.now()
) {
    val effectivePrice: BigDecimal
        get() = if (isBonus && bonusPrice != null) bonusPrice!! else (price ?: BigDecimal.ZERO)
}

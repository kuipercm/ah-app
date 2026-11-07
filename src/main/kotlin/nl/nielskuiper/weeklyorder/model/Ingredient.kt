package nl.nielskuiper.weeklyorder.model

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*

@Entity
@Table(name = "ingredients")
class Ingredient(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    var quantity: Double = 1.0,

    var unit: String? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_webshop_id")
    var matchedProduct: Product? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    @JsonBackReference
    var recipe: Recipe? = null
)

package nl.nielskuiper.weeklyorder.model

import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "recipes")
class Recipe(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(length = 2000)
    var notes: String? = null,

    var defaultServings: Int = 2,

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    var ingredients: MutableList<Ingredient> = mutableListOf(),

    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun addIngredient(ingredient: Ingredient) {
        ingredients.add(ingredient)
        ingredient.recipe = this
    }

    fun removeIngredient(ingredient: Ingredient) {
        ingredients.remove(ingredient)
        ingredient.recipe = null
    }
}

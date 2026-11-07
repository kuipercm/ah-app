package nl.nielskuiper.weeklyorder

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ControllerIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET api recipes returns recipe list`() {
        mockMvc.perform(get("/api/recipes"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `POST api recipes bulk-parse imports recipes from text`() {
        val payload = """
        {
          "text": "Ovenschotel met prei (prei, gehakt, aardappelschijfjes, kaas) -- Vriezer: geraspte kaas opmaken",
          "autoMatch": false
        }
        """.trimIndent()

        mockMvc.perform(
            post("/api/recipes/bulk-parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Ovenschotel met prei"))
            .andExpect(jsonPath("$[0].notes").value("Vriezer: geraspte kaas opmaken"))
    }

    @Test
    fun `GET api staples evaluated returns weekly, due, and bonus pull-forward items`() {
        mockMvc.perform(get("/api/staples/evaluated"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.weeklyItems").isArray)
            .andExpect(jsonPath("$.occasionalDue").isArray)
            .andExpect(jsonPath("$.occasionalBonusPullForward").isArray)
    }

    @Test
    fun `GET api planner upcoming returns upcoming Tuesday plan`() {
        mockMvc.perform(get("/api/planner/upcoming"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetDeliveryDate").exists())
            .andExpect(jsonPath("$.status").value("DRAFT"))
    }
}

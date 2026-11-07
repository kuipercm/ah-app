package nl.nielskuiper.weeklyorder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File

@SpringBootApplication
@EnableScheduling
class WeeklyOrderApplication {
    companion object {
        init {
            val dbPath = System.getenv("DB_PATH")
            if (!dbPath.isNullOrBlank()) {
                val parent = File(dbPath).parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
            } else {
                val dataDir = File("./data")
                if (!dataDir.exists()) {
                    dataDir.mkdirs()
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<WeeklyOrderApplication>(*args)
}

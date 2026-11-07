package nl.nielskuiper.weeklyorder.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "ah_credentials")
class AhCredential(
    @Id
    var id: Long = 1L,

    @Column(name = "access_token", length = 4000)
    var accessToken: String? = null,

    @Column(name = "refresh_token", length = 4000)
    var refreshToken: String? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "user_name")
    var userName: String? = null,

    @Column(name = "member_id")
    var memberId: String? = null,

    @Column(name = "last_synced_at")
    var lastSyncedAt: LocalDateTime? = null
) {
    val isExpired: Boolean
        get() = expiresAt == null || LocalDateTime.now().isAfter(expiresAt!!.minusMinutes(5))
}

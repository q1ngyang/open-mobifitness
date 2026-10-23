package org.openmobifitness.app.ble

/** Finite budgets for a user-requested connection, including one recovery attempt. */
data class ConnectionTimeouts(
    val scanMs: Long=10_000,
    val connectMs: Long=20_000,
    val discoveryMs: Long=15_000,
    val initializationMs: Long=60_000,
    val totalMs: Long=90_000,
    val retryDelayMs: Long=1_000
)

data class ScanTrace(
    val id: Long=0, val purpose: String="none", val active: Boolean=false,
    val startedAt: Long=0, val endedAt: Long=0,
    val reports: Int=0, val accepted: Int=0, val uniqueDevices: Int=0,
    val targetSeen: Boolean=false, val outcome: String="none"
)

val LinkState.connectionPending: Boolean
    get()=phase in setOf("searching","connecting","discovering","initializing")

/** Android limits scan starts per application, including the separate heart-rate client. */
class ScanBudget {
    private val starts=ArrayDeque<Long>()
    private var blockedUntil=0L
    @Synchronized fun take(now: Long): Boolean {
        while(starts.isNotEmpty() && now-starts.first()>=30_000) starts.removeFirst()
        if(now<blockedUntil || starts.size>=4) return false
        starts.addLast(now)
        return true
    }
    @Synchronized fun throttled(now: Long) { blockedUntil=now+30_000 }
}

/** Remembering a disconnected address must not hide a fresh advertisement. */
fun nearbyDevices(found: List<FoundDevice>, equipment: LinkState, heart: LinkState): List<FoundDevice> =
    found.filter { device ->
        (equipment.phase=="disconnected" || device.address!=equipment.address) &&
        (heart.phase=="disconnected" || device.address!=heart.address)
    }

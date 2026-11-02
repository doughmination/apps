package gay.doughmination.plural.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The shapes of /v2/plural/*, as the API returns them (mirrors
// react-api/src/types/plural.ts). Members are PluralKit's member objects plus
// the API's own tags, pride and status; PluralKit leaves out whatever a
// member's privacy settings hide, so nearly everything is optional.

@Serializable
data class MemberStatus(
    val text: String,
    val emoji: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Member(
    /** PluralKit's short id ("abcdef"). */
    val id: String,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    /** Hex without the "#", as PluralKit stores it. */
    val color: String? = null,
    val pronouns: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val pride: List<String> = emptyList(),
    val status: MemberStatus? = null,
) {
    /** The name people see, as the website picks it. */
    val shownName: String get() = displayName?.ifBlank { null } ?: name?.ifBlank { null } ?: id

    /** What the API's tag, pride and status endpoints take: the member's name. */
    val key: String get() = name ?: id

    fun isSame(identifier: String) = identifier == id || identifier == name
}

@Serializable
data class Fronters(
    /** When the current switch happened. */
    val timestamp: String? = null,
    /** Absent when nobody is fronting. */
    val members: List<Member> = emptyList(),
)

@Serializable
data class MentalState(
    val level: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class PluralSystem(
    val name: String? = null,
    val description: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("mental_state") val mentalState: MentalState? = null,
)

@Serializable
data class Relationship(
    val id: String,
    /** Two member ids (or names). For a crush, the first has a crush on the second. */
    val members: List<String>,
    val type: String = "partner",
    val since: String? = null,
)

@Serializable
data class RelationshipsResponse(val relationships: List<Relationship> = emptyList())

@Serializable
data class MemberStatusResponse(val status: MemberStatus? = null)

/** The windows the metrics endpoints report on, as the website labels them. */
enum class Timeframe(val key: String, val label: String) {
    Day("24h", "Last 24 Hours"),
    TwoDays("48h", "Last 48 Hours"),
    FiveDays("5d", "Last 5 Days"),
    Week("7d", "Last 7 Days"),
    Month("30d", "Last 30 Days"),
}

@Serializable
data class MemberMetric(
    val id: String,
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("total_seconds") val totalSeconds: Double = 0.0,
    @SerialName("24h") val day: Double = 0.0,
    @SerialName("48h") val twoDays: Double = 0.0,
    @SerialName("5d") val fiveDays: Double = 0.0,
    @SerialName("7d") val week: Double = 0.0,
    @SerialName("30d") val month: Double = 0.0,
) {
    val shownName: String get() = displayName?.ifBlank { null } ?: name ?: id

    /** Seconds fronted in a window. */
    operator fun get(timeframe: Timeframe): Double = when (timeframe) {
        Timeframe.Day -> day
        Timeframe.TwoDays -> twoDays
        Timeframe.FiveDays -> fiveDays
        Timeframe.Week -> week
        Timeframe.Month -> month
    }
}

@Serializable
data class FrontingMetrics(
    @SerialName("total_time") val totalTime: Double = 0.0,
    val members: Map<String, MemberMetric> = emptyMap(),
)

@Serializable
data class SwitchMetrics(
    @SerialName("total_switches") val totalSwitches: Int = 0,
    @SerialName("avg_switches_per_day") val averagePerDay: Double = 0.0,
    val timeframes: Map<String, Int> = emptyMap(),
) {
    operator fun get(timeframe: Timeframe): Int = timeframes[timeframe.key] ?: 0
}

@Serializable
data class User(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("is_owner") val isOwner: Boolean = false,
    @SerialName("is_pet") val isPet: Boolean = false,
    @SerialName("avatar_url") val avatarUrl: String? = null,
) {
    val shownName: String get() = displayName?.ifBlank { null } ?: username
}

/** The mental-state levels the website offers, in its order. */
enum class MentalLevel(val value: String, val label: String, val severity: Int) {
    Safe("safe", "Safe", 0),
    Unstable("unstable", "Unstable", 1),
    Idealizing("idealizing", "Idealizing", 1),
    SelfHarming("self-harming", "Self-Harming", 2),
    HighlyAtRisk("highly at risk", "Highly At Risk", 2);

    companion object {
        fun of(value: String): MentalLevel? = entries.firstOrNull { it.value == value }
    }
}

/** The relationship types the website's admin page offers. */
val RelationshipTypes = listOf("partner", "spouse", "queerplatonic", "datemate", "crush")

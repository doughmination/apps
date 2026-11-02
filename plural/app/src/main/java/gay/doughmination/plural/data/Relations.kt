package gay.doughmination.plural.data

// How the website reads the relationship list (system/src/routes/relationships
// and [member_id]/+page.ts): crushes go one way (the first member has a crush
// on the second), and two crushes on each other fold into one mutual crush.

class Couple(val edge: Relationship, val a: Member, val b: Member, val mutual: Boolean) {
    val label: String
        get() = when {
            edge.type != "crush" -> edge.type
            mutual -> "mutual crush"
            else -> "has a crush on"
        }
}

fun List<Member>.find(identifier: String): Member? = firstOrNull { it.isSame(identifier) }

/** Every relationship between two known members, mutual crushes folded together. */
fun pairs(relationships: List<Relationship>, members: List<Member>): List<Couple> {
    val list = mutableListOf<Couple>()
    for (edge in relationships) {
        val a = members.find(edge.members.getOrNull(0) ?: continue) ?: continue
        val b = members.find(edge.members.getOrNull(1) ?: continue) ?: continue
        if (a.id == b.id) continue
        val reverse = list.indexOfFirst { it.edge.type == "crush" && edge.type == "crush" && it.a.id == b.id && it.b.id == a.id }
        if (reverse >= 0) {
            val old = list[reverse]
            list[reverse] = Couple(old.edge, old.a, old.b, mutual = true)
        } else {
            list += Couple(edge, a, b, mutual = false)
        }
    }
    return list
}

class Crush(val member: Member, val mutual: Boolean)

/** A member's partners (any type but crush), the website's member page. */
fun partnersOf(member: Member, relationships: List<Relationship>, members: List<Member>): List<Member> =
    relationships.filter { it.type != "crush" }.mapNotNull { edge ->
        when {
            member.isSame(edge.members[0]) -> members.find(edge.members[1])
            member.isSame(edge.members[1]) -> members.find(edge.members[0])
            else -> null
        }
    }.distinctBy { it.id }

/** Who a member has a crush on; mutual when the crush goes both ways. */
fun crushesOf(member: Member, relationships: List<Relationship>, members: List<Member>): List<Crush> {
    val crushes = relationships.filter { it.type == "crush" }
    val crushedBy = crushes.filter { member.isSame(it.members[1]) }.mapNotNull { members.find(it.members[0]) }
    return crushes.filter { member.isSame(it.members[0]) }
        .mapNotNull { members.find(it.members[1]) }
        .distinctBy { it.id }
        .map { crush -> Crush(crush, mutual = crushedBy.any { it.id == crush.id }) }
}

package gay.doughmination.plural.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// The relationship map's 3D force layout, ported from system/src/lib/graph.ts
// so the app's map settles into the same shape as the website's.
// Deterministic: no randomness, so it looks the same every time.

data class Point3(val x: Double, val y: Double, val z: Double)

/** Where a point lands on screen: depth 0 is the back of the scene, 1 the front. */
data class Projected(val x: Double, val y: Double, val depth: Double, val scale: Double)

private const val ITERATIONS = 400
private const val REST_LENGTH = 120.0
private const val SPRING = 0.04
private const val REPULSION = 60000.0
private const val GRAVITY = 0.06
private const val START_RADIUS = 220.0
private const val CAMERA = 900.0

/** Starting points spread evenly over a sphere (a Fibonacci lattice). */
private fun sphere(count: Int): List<DoubleArray> {
    val golden = PI * (3 - sqrt(5.0))
    return List(count) { index ->
        val y = if (count == 1) 0.0 else 1 - (index.toDouble() / (count - 1)) * 2
        val ring = sqrt(1 - y * y)
        val angle = golden * index
        doubleArrayOf(cos(angle) * ring * START_RADIUS, y * START_RADIUS, sin(angle) * ring * START_RADIUS)
    }
}

/**
 * Lays out `count` nodes joined by `links`: they push apart, links pull like
 * springs, a little gravity keeps them together. Then centred and scaled so
 * the furthest node sits on `radius`.
 */
fun layout(count: Int, links: List<Pair<Int, Int>>, radius: Double): List<Point3> {
    if (count == 0) return emptyList()
    val points = sphere(count)

    for (step in 0 until ITERATIONS) {
        val cooling = 1 - step.toDouble() / ITERATIONS
        val force = List(count) { DoubleArray(3) }

        for (a in 0 until count) {
            for (b in a + 1 until count) {
                val dx = points[a][0] - points[b][0]
                val dy = points[a][1] - points[b][1]
                val dz = points[a][2] - points[b][2]
                val distanceSq = max(dx * dx + dy * dy + dz * dz, 1.0)
                val push = REPULSION / distanceSq / sqrt(distanceSq)
                force[a][0] += dx * push; force[a][1] += dy * push; force[a][2] += dz * push
                force[b][0] -= dx * push; force[b][1] -= dy * push; force[b][2] -= dz * push
            }
        }

        for ((a, b) in links) {
            val dx = points[b][0] - points[a][0]
            val dy = points[b][1] - points[a][1]
            val dz = points[b][2] - points[a][2]
            val distance = max(sqrt(dx * dx + dy * dy + dz * dz), 1.0)
            val pull = SPRING * (distance - REST_LENGTH) / distance
            force[a][0] += dx * pull; force[a][1] += dy * pull; force[a][2] += dz * pull
            force[b][0] -= dx * pull; force[b][1] -= dy * pull; force[b][2] -= dz * pull
        }

        for (i in 0 until count) {
            for (axis in 0..2) {
                points[i][axis] += (force[i][axis] - points[i][axis] * GRAVITY) * cooling
            }
        }
    }

    val centre = DoubleArray(3) { axis -> points.sumOf { it[axis] } / count }
    val furthest = max(1.0, points.maxOf { hypot(hypot(it[0] - centre[0], it[1] - centre[1]), it[2] - centre[2]) })
    val fit = radius / furthest
    return points.map { Point3((it[0] - centre[0]) * fit, (it[1] - centre[1]) * fit, (it[2] - centre[2]) * fit) }
}

/** Rotates by yaw (round the vertical axis) then pitch, and projects with perspective. */
fun project(point: Point3, yaw: Double, pitch: Double, radius: Double): Projected {
    val x1 = point.x * cos(yaw) - point.z * sin(yaw)
    val z1 = point.x * sin(yaw) + point.z * cos(yaw)
    val y2 = point.y * cos(pitch) - z1 * sin(pitch)
    val z2 = point.y * sin(pitch) + z1 * cos(pitch)
    val scale = CAMERA / (CAMERA + z2)
    return Projected(x1 * scale, y2 * scale, 1 - (z2 + radius) / (2 * radius), scale)
}

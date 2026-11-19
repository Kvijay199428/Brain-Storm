package com.vegasol.brainstorm

import com.vegasol.brainstorm.mascot.MascotExpression
import com.vegasol.brainstorm.mascot.MascotGesture
import com.vegasol.brainstorm.mascot.MascotState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the contract between the typed Kotlin bridge and `mascot.html`.
 *
 * The audit that produced these fixes found two typed expression ids that did not exist in
 * the JavaScript FACE table and six gesture ids with no pose mapping: the calls compiled and
 * persisted fine and then did nothing at runtime. These tests fail instead.
 */
class MascotBridgeContractTest {

    private val html: String by lazy {
        val candidates = listOf(
            File("src/main/assets/mascot.html"),
            File("app/src/main/assets/mascot.html")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("mascot.html not found from ${File(".").absolutePath}")
        file.readText()
    }

    private fun block(start: String, end: String): String {
        val from = html.indexOf(start)
        val to = if (from < 0) -1 else html.indexOf(end, from + start.length)
        assertTrue("marker '$start' missing from mascot.html", from >= 0 && to > from)
        return html.substring(from, to)
    }

    @Test
    fun `every MascotState key exists in the JavaScript state table`() {
        val st = block("var ST={", "function mk(")
        val keys = Regex("""(?m)^\s*(\w+):\{""").findAll(st).map { it.groupValues[1] }.toSet()
        assertTrue("state table looks empty: $keys", keys.isNotEmpty())
        MascotState.values().forEach { state ->
            assertTrue("${state.name} -> '${state.jsKey}' is not in the JS state table", state.jsKey in keys)
        }
    }

    @Test
    fun `every MascotExpression key exists in the JavaScript FACE table`() {
        val ft = block(",FT={", "for(var n in FT)")
        val keys = Regex("""(\w+):\[""").findAll(ft).map { it.groupValues[1] }.toSet()
        assertTrue("face table looks empty: $keys", keys.isNotEmpty())
        MascotExpression.values().forEach { expression ->
            assertTrue(
                "${expression.name} -> '${expression.jsKey}' is not in the JS FACE table",
                expression.jsKey in keys
            )
        }
    }

    @Test
    fun `every MascotGesture key has a pose mapping`() {
        val map = block("var gestureMap={", "};")
        val keys = Regex("""(\w+):\{""").findAll(map).map { it.groupValues[1] }.toSet()
        assertTrue("gesture map looks empty: $keys", keys.isNotEmpty())
        MascotGesture.values().forEach { gesture ->
            assertTrue(
                "${gesture.name} -> '${gesture.jsKey}' has no hand/arm mapping",
                gesture.jsKey in keys
            )
        }
    }
}

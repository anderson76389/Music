package fr.makoff.music.eq.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoEqTreeIndexTest {
    @Test fun completeTreeKeepsOnlyProfiles() {
        val nodes = collectAutoEqProfiles("results") { _, recursive ->
            check(recursive)
            AutoEqTreeResponse("results", listOf(
                AutoEqTreeNode("source/over-ear/HD 600/HD 600 ParametricEQ.txt", "blob"),
                AutoEqTreeNode("README.md", "blob"),
            ))
        }
        assertEquals(listOf("results/source/over-ear/HD 600/HD 600 ParametricEQ.txt"), nodes.map { it.path })
    }

    @Test fun truncatedTreeIsRebuiltFromAllChildren() {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val nodes = collectAutoEqProfiles("results") { sha, recursive ->
            calls += sha to recursive
            when {
                sha == "results" && recursive -> AutoEqTreeResponse(sha, listOf(
                    AutoEqTreeNode("partial ParametricEQ.txt", "blob")), truncated = true)
                sha == "results" -> AutoEqTreeResponse(sha, listOf(
                    AutoEqTreeNode("first", "tree", "a"), AutoEqTreeNode("last", "tree", "z")))
                else -> AutoEqTreeResponse(sha, listOf(AutoEqTreeNode("model/model ParametricEQ.txt", "blob")))
            }
        }
        assertEquals(listOf("results/first/model/model ParametricEQ.txt", "results/last/model/model ParametricEQ.txt"), nodes.map { it.path })
        assertEquals(listOf("results" to true, "results" to false, "a" to true, "z" to true), calls)
    }

    @Test(expected = IllegalStateException::class)
    fun incompleteDirectoryCannotBecomeSuccessfulIndex() {
        collectAutoEqProfiles("results") { sha, _ -> AutoEqTreeResponse(sha, emptyList(), truncated = true) }
    }
}

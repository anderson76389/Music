package fr.makoff.music.eq.data

import kotlinx.serialization.Serializable

@Serializable
internal data class AutoEqTreeResponse(
    val sha: String,
    val tree: List<AutoEqTreeNode>,
    val truncated: Boolean = false,
)

@Serializable
internal data class AutoEqTreeNode(
    val path: String,
    val type: String,
    val sha: String? = null,
)

// GitHub limits recursive tree responses. Split only truncated subtrees, and
// publish an index only after every subtree has been retrieved successfully.
internal fun collectAutoEqProfiles(
    resultsSha: String,
    fetch: (String, Boolean) -> AutoEqTreeResponse,
): List<AutoEqTreeNode> {
    var requests = 0
    fun request(sha: String, recursive: Boolean): AutoEqTreeResponse {
        check(++requests <= 128) { "AutoEQ tree request limit exceeded" }
        return fetch(sha, recursive)
    }
    fun visit(sha: String, prefix: String, depth: Int): List<AutoEqTreeNode> {
        check(depth <= 16) { "AutoEQ tree depth exceeded" }
        val recursive = request(sha, true)
        val nodes = if (!recursive.truncated) {
            recursive.tree.map { it.copy(path = prefix + it.path) }
        } else {
            val direct = request(sha, false)
            check(!direct.truncated) { "Incomplete AutoEQ directory" }
            direct.tree.flatMap { node ->
                if (node.type == "tree") {
                    visit(requireNotNull(node.sha), prefix + node.path + "/", depth + 1)
                } else {
                    listOf(node.copy(path = prefix + node.path))
                }
            }
        }
        return nodes.filter { it.type == "blob" && it.path.endsWith(" ParametricEQ.txt") }
    }
    return visit(resultsSha, "results/", 0)
}

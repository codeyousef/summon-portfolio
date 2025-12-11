package code.yousef.portfolio.docs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class DocsConfig(
    val githubOwner: String,
    val githubRepo: String,
    val defaultBranch: String,
    val docsRoot: String,
    val useContentsApi: Boolean,
    val githubToken: String?,
    val cacheTtlSeconds: Long,
    val enableRedis: Boolean,
    val publicOriginDocs: String,
    val publicOriginPortfolio: String,
    val docsSource: DocsSource,
    val localDocsRoot: Path,
    val githubRawBase: String = "https://raw.githubusercontent.com"
) {
    val normalizedDocsRoot: String = docsRoot.trim().trim('/').ifBlank { "docs" }

    fun rawFileUrl(branch: String, repoPath: String): String {
        val sanitizedPath = repoPath.trimStart('/')
        return "$githubRawBase/$githubOwner/$githubRepo/$branch/$sanitizedPath"
    }

    fun repoPathFor(relativePath: String): String {
        val cleanRelative = relativePath.trimStart('/')
        return "${normalizedDocsRoot}/${cleanRelative}".replace("//", "/")
    }

    companion object {
        /**
         * Creates a DocsConfig for Summon docs from environment variables.
         */
        fun fromEnv(): DocsConfig = summonFromEnv()

        /**
         * Creates a DocsConfig for Summon docs.
         */
        fun summonFromEnv(): DocsConfig {
            val env = System.getenv()
            val owner = env["DOCS_GITHUB_OWNER"] ?: "codeyousef"
            val repo = env["DOCS_GITHUB_REPO"] ?: "summon"
            val branch = env["DOCS_BRANCH"] ?: "main"
            val root = env["DOCS_ROOT"] ?: "docs"
            val useApi = (env["DOCS_USE_API"] ?: "false").toBooleanStrictOrNull() ?: false
            val ttl = (env["DOCS_CACHE_TTL_SECONDS"] ?: "3600").toLongOrNull() ?: 3600L
            val enableRedis = (env["DOCS_ENABLE_REDIS"] ?: "false").toBooleanStrictOrNull() ?: false
            val docsSource = when ((env["DOCS_SOURCE"] ?: "remote").lowercase()) {
                "local" -> DocsSource.LOCAL
                else -> DocsSource.REMOTE
            }
            val localRoot = env["DOCS_LOCAL_ROOT"]?.let { Paths.get(it) } ?: run {
                val summonDocs = Paths.get("docs/private/summon-docs")
                if (Files.exists(summonDocs)) summonDocs else Paths.get("docs")
            }
            val docsOrigin = env["PUBLIC_ORIGIN_DOCS"] ?: "https://summon.yousef.codes"
            val portfolioOrigin = env["PUBLIC_ORIGIN_PORTFOLIO"] ?: "https://www.yousef.codes"
            return DocsConfig(
                githubOwner = owner,
                githubRepo = repo,
                defaultBranch = branch,
                docsRoot = root,
                useContentsApi = useApi,
                githubToken = env["GITHUB_TOKEN"],
                cacheTtlSeconds = ttl,
                enableRedis = enableRedis,
                publicOriginDocs = docsOrigin,
                publicOriginPortfolio = portfolioOrigin,
                docsSource = docsSource,
                localDocsRoot = localRoot
            )
        }

        /**
         * Creates a DocsConfig for Materia docs.
         * Uses MATERIA_* environment variables with fallbacks.
         */
        fun materiaFromEnv(): DocsConfig {
            val env = System.getenv()
            val owner = env["MATERIA_GITHUB_OWNER"] ?: "codeyousef"
            val repo = env["MATERIA_GITHUB_REPO"] ?: "Materia"
            val branch = env["MATERIA_DOCS_BRANCH"] ?: "main"
            val root = env["MATERIA_DOCS_ROOT"] ?: "docs"
            val useApi = (env["MATERIA_DOCS_USE_API"] ?: "false").toBooleanStrictOrNull() ?: false
            val ttl = (env["DOCS_CACHE_TTL_SECONDS"] ?: "3600").toLongOrNull() ?: 3600L
            val enableRedis = (env["DOCS_ENABLE_REDIS"] ?: "false").toBooleanStrictOrNull() ?: false
            val docsSource = when ((env["MATERIA_DOCS_SOURCE"] ?: "remote").lowercase()) {
                "local" -> DocsSource.LOCAL
                else -> DocsSource.REMOTE
            }
            val localRoot = env["MATERIA_DOCS_LOCAL_ROOT"]?.let { Paths.get(it) } ?: run {
                val materiaDocs = Paths.get("docs/private/materia-docs")
                if (Files.exists(materiaDocs)) materiaDocs else Paths.get("docs")
            }
            val docsOrigin = env["PUBLIC_ORIGIN_MATERIA_DOCS"] ?: "https://materia.yousef.codes"
            val portfolioOrigin = env["PUBLIC_ORIGIN_PORTFOLIO"] ?: "https://www.yousef.codes"
            return DocsConfig(
                githubOwner = owner,
                githubRepo = repo,
                defaultBranch = branch,
                docsRoot = root,
                useContentsApi = useApi,
                githubToken = env["GITHUB_TOKEN"],
                cacheTtlSeconds = ttl,
                enableRedis = enableRedis,
                publicOriginDocs = docsOrigin,
                publicOriginPortfolio = portfolioOrigin,
                docsSource = docsSource,
                localDocsRoot = localRoot
            )
        }

        /**
         * Creates a DocsConfig for Sigil docs.
         * Uses SIGIL_* environment variables with fallbacks.
         */
        fun sigilFromEnv(): DocsConfig {
            val env = System.getenv()
            val owner = env["SIGIL_GITHUB_OWNER"] ?: "codeyousef"
            val repo = env["SIGIL_GITHUB_REPO"] ?: "sigil"
            val branch = env["SIGIL_DOCS_BRANCH"] ?: "main"
            val root = env["SIGIL_DOCS_ROOT"] ?: "docs"
            val useApi = (env["SIGIL_DOCS_USE_API"] ?: "false").toBooleanStrictOrNull() ?: false
            val ttl = (env["DOCS_CACHE_TTL_SECONDS"] ?: "3600").toLongOrNull() ?: 3600L
            val enableRedis = (env["DOCS_ENABLE_REDIS"] ?: "false").toBooleanStrictOrNull() ?: false
            val docsSource = when ((env["SIGIL_DOCS_SOURCE"] ?: "remote").lowercase()) {
                "local" -> DocsSource.LOCAL
                else -> DocsSource.REMOTE
            }
            val localRoot = env["SIGIL_DOCS_LOCAL_ROOT"]?.let { Paths.get(it) } ?: run {
                val sigilDocs = Paths.get("docs/private/sigil-docs")
                if (Files.exists(sigilDocs)) sigilDocs else Paths.get("docs")
            }
            val docsOrigin = env["PUBLIC_ORIGIN_SIGIL_DOCS"] ?: "https://sigil.yousef.codes"
            val portfolioOrigin = env["PUBLIC_ORIGIN_PORTFOLIO"] ?: "https://www.yousef.codes"
            return DocsConfig(
                githubOwner = owner,
                githubRepo = repo,
                defaultBranch = branch,
                docsRoot = root,
                useContentsApi = useApi,
                githubToken = env["GITHUB_TOKEN"],
                cacheTtlSeconds = ttl,
                enableRedis = enableRedis,
                publicOriginDocs = docsOrigin,
                publicOriginPortfolio = portfolioOrigin,
                docsSource = docsSource,
                localDocsRoot = localRoot
            )
        }
    }
}

enum class DocsSource {
    REMOTE,
    LOCAL
}

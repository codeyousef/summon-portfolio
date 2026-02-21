package code.yousef.portfolio.ssr

import java.util.*

data class EnvironmentLinks(
    val portfolioBase: String,
    val blogBase: String,
    val summonBase: String,
    val docsBase: String,
    val materiaBase: String,
    val materiaDocsBase: String,
    val sigilBase: String,
    val sigilDocsBase: String,
    val aetherBase: String,
    val aetherDocsBase: String
)

private enum class DeploymentStage {
    DEV,
    PROD,
    LOCAL
}

private val stageLinks = mapOf(
    DeploymentStage.DEV to EnvironmentLinks(
        portfolioBase = "https://dev.yousef.codes",
        blogBase = "https://dev.yousef.codes/blog",
        summonBase = "https://summon.dev.yousef.codes",
        docsBase = "https://summon.dev.yousef.codes/docs",
        materiaBase = "https://materia.dev.yousef.codes",
        materiaDocsBase = "https://materia.dev.yousef.codes/docs",
        sigilBase = "https://sigil.dev.yousef.codes",
        sigilDocsBase = "https://sigil.dev.yousef.codes/docs",
        aetherBase = "https://aether.dev.yousef.codes",
        aetherDocsBase = "https://aether.dev.yousef.codes/docs"
    ),
    DeploymentStage.PROD to EnvironmentLinks(
        portfolioBase = "https://yousef.codes",
        blogBase = "https://yousef.codes/blog",
        summonBase = "https://summon.yousef.codes",
        docsBase = "https://summon.yousef.codes/docs",
        materiaBase = "https://materia.yousef.codes",
        materiaDocsBase = "https://materia.yousef.codes/docs",
        sigilBase = "https://sigil.yousef.codes",
        sigilDocsBase = "https://sigil.yousef.codes/docs",
        aetherBase = "https://aether.yousef.codes",
        aetherDocsBase = "https://aether.yousef.codes/docs"
    ),
    DeploymentStage.LOCAL to EnvironmentLinks(
        portfolioBase = "http://localhost:8080",
        blogBase = "http://localhost:8080/blog",
        summonBase = "http://localhost:8080/summon",
        docsBase = "http://localhost:8080/summon/docs",
        materiaBase = "http://localhost:8080/materia",
        materiaDocsBase = "http://localhost:8080/materia/docs",
        sigilBase = "http://localhost:8080/sigil",
        sigilDocsBase = "http://localhost:8080/sigil/docs",
        aetherBase = "http://localhost:8080/aether",
        aetherDocsBase = "http://localhost:8080/aether/docs"
    )
)

private val docsBaseOverride = System.getenv("DOCS_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val summonBaseOverride = System.getenv("SUMMON_MARKETING_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val materiaBaseOverride = System.getenv("MATERIA_MARKETING_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val materiaDocsBaseOverride = System.getenv("MATERIA_DOCS_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val sigilBaseOverride = System.getenv("SIGIL_MARKETING_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val sigilDocsBaseOverride = System.getenv("SIGIL_DOCS_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val aetherBaseOverride = System.getenv("AETHER_MARKETING_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val aetherDocsBaseOverride = System.getenv("AETHER_DOCS_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')

object EnvironmentLinksRegistry {
    private val threadLocal = ThreadLocal<EnvironmentLinks>()

    fun current(): EnvironmentLinks? = threadLocal.get()

    suspend fun <T> withLinks(links: EnvironmentLinks, block: suspend () -> T): T {
        try {
            threadLocal.set(links)
            return block()
        } finally {
            threadLocal.remove()
        }
    }
}

private fun stageForHost(host: String?): DeploymentStage {
    val normalized = host?.lowercase(Locale.US)?.substringBefore(":") ?: return DeploymentStage.LOCAL
    return when {
        normalized.contains("dev.yousef.codes") -> DeploymentStage.DEV
        normalized.contains("yousef.codes") -> DeploymentStage.PROD
        else -> DeploymentStage.LOCAL
    }
}

fun resolveEnvironmentLinks(host: String?): EnvironmentLinks {
    val stage = stageForHost(host)
    var baseline = stageLinks[stage] ?: stageLinks.getValue(DeploymentStage.LOCAL)

    // If we are in LOCAL stage and have a valid host (e.g. LAN IP or tunnel), use it dynamically
    if (stage == DeploymentStage.LOCAL && !host.isNullOrBlank() && !host.contains("localhost")) {
        val base = "http://$host"
        baseline = baseline.copy(
            portfolioBase = base,
            blogBase = "$base/blog",
            summonBase = "$base/summon",
            docsBase = "$base/summon/docs",
            materiaBase = "$base/materia",
            materiaDocsBase = "$base/materia/docs",
            sigilBase = "$base/sigil",
            sigilDocsBase = "$base/sigil/docs",
            aetherBase = "$base/aether",
            aetherDocsBase = "$base/aether/docs"
        )
    }

    val summonBase = summonBaseOverride ?: baseline.summonBase
    val docsBase = docsBaseOverride ?: baseline.docsBase
    val materiaBase = materiaBaseOverride ?: baseline.materiaBase
    val materiaDocsBase = materiaDocsBaseOverride ?: baseline.materiaDocsBase
    val sigilBase = sigilBaseOverride ?: baseline.sigilBase
    val sigilDocsBase = sigilDocsBaseOverride ?: baseline.sigilDocsBase
    val aetherBase = aetherBaseOverride ?: baseline.aetherBase
    val aetherDocsBase = aetherDocsBaseOverride ?: baseline.aetherDocsBase
    return baseline.copy(
        summonBase = summonBase,
        docsBase = docsBase,
        materiaBase = materiaBase,
        materiaDocsBase = materiaDocsBase,
        sigilBase = sigilBase,
        sigilDocsBase = sigilDocsBase,
        aetherBase = aetherBase,
        aetherDocsBase = aetherDocsBase
    )
}

fun portfolioBaseUrl(): String = EnvironmentLinksRegistry.current()?.portfolioBase ?: SITE_URL

fun blogUrl(): String = EnvironmentLinksRegistry.current()?.blogBase ?: "${portfolioBaseUrl().trimEnd('/')}/blog"

fun summonMarketingUrl(): String =
    EnvironmentLinksRegistry.current()?.summonBase ?: (summonBaseOverride ?: SUMMON_MARKETING_URL)

fun docsBaseUrl(): String {
    EnvironmentLinksRegistry.current()?.docsBase?.let { return it }
    docsBaseOverride?.let { return it }
    return summonMarketingUrl().trimEnd('/')
}

fun materiaMarketingUrl(): String =
    EnvironmentLinksRegistry.current()?.materiaBase ?: (materiaBaseOverride ?: MATERIA_MARKETING_URL)

fun materiaDocsBaseUrl(): String {
    EnvironmentLinksRegistry.current()?.materiaDocsBase?.let { return it }
    materiaDocsBaseOverride?.let { return it }
    return materiaMarketingUrl().trimEnd('/') + "/docs"
}

fun sigilMarketingUrl(): String =
    EnvironmentLinksRegistry.current()?.sigilBase ?: (sigilBaseOverride ?: SIGIL_MARKETING_URL)

fun sigilDocsBaseUrl(): String {
    EnvironmentLinksRegistry.current()?.sigilDocsBase?.let { return it }
    sigilDocsBaseOverride?.let { return it }
    return sigilMarketingUrl().trimEnd('/') + "/docs"
}

fun aetherMarketingUrl(): String =
    EnvironmentLinksRegistry.current()?.aetherBase ?: (aetherBaseOverride ?: AETHER_MARKETING_URL)

fun aetherDocsBaseUrl(): String {
    EnvironmentLinksRegistry.current()?.aetherDocsBase?.let { return it }
    aetherDocsBaseOverride?.let { return it }
    return aetherMarketingUrl().trimEnd('/') + "/docs"
}

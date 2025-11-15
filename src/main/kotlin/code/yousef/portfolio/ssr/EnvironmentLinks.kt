package code.yousef.portfolio.ssr

import java.util.*

data class EnvironmentLinks(
    val portfolioBase: String,
    val summonBase: String,
    val docsBase: String
)

private enum class DeploymentStage {
    DEV,
    UAT,
    PROD,
    LOCAL
}

private val stageLinks = mapOf(
    DeploymentStage.DEV to EnvironmentLinks(
        portfolioBase = "https://dev.yousef.codes",
        summonBase = "https://summon.dev.yousef.codes",
        docsBase = "https://summon.dev.yousef.codes"
    ),
    DeploymentStage.UAT to EnvironmentLinks(
        portfolioBase = "https://uat.yousef.codes",
        summonBase = "https://summon.uat.yousef.codes",
        docsBase = "https://summon.uat.yousef.codes"
    ),
    DeploymentStage.PROD to EnvironmentLinks(
        portfolioBase = "https://yousef.codes",
        summonBase = "https://summon.yousef.codes",
        docsBase = "https://summon.yousef.codes"
    ),
    DeploymentStage.LOCAL to EnvironmentLinks(
        portfolioBase = SITE_URL,
        summonBase = SUMMON_MARKETING_URL,
        docsBase = SUMMON_MARKETING_URL.trimEnd('/')
    )
)

private val docsBaseOverride = System.getenv("DOCS_BASE_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')
private val summonBaseOverride = System.getenv("SUMMON_MARKETING_URL")?.takeIf { it.isNotBlank() }?.trimEnd('/')

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
        normalized.contains("uat.yousef.codes") -> DeploymentStage.UAT
        normalized.contains("yousef.codes") -> DeploymentStage.PROD
        else -> DeploymentStage.LOCAL
    }
}

fun resolveEnvironmentLinks(host: String?): EnvironmentLinks {
    val stage = stageForHost(host)
    val baseline = stageLinks[stage] ?: stageLinks.getValue(DeploymentStage.LOCAL)
    val summonBase = summonBaseOverride ?: baseline.summonBase
    val docsBase = docsBaseOverride ?: baseline.docsBase
    return baseline.copy(
        summonBase = summonBase,
        docsBase = docsBase
    )
}

fun portfolioBaseUrl(): String = EnvironmentLinksRegistry.current()?.portfolioBase ?: SITE_URL

fun summonMarketingUrl(): String =
    EnvironmentLinksRegistry.current()?.summonBase ?: (summonBaseOverride ?: SUMMON_MARKETING_URL)

fun docsBaseUrl(): String {
    EnvironmentLinksRegistry.current()?.docsBase?.let { return it }
    docsBaseOverride?.let { return it }
    return summonMarketingUrl().trimEnd('/')
}

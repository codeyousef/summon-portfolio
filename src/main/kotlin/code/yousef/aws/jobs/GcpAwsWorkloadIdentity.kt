package code.yousef.aws.jobs

import com.google.auth.oauth2.AwsCredentials
import com.google.auth.oauth2.AwsSecurityCredentials
import com.google.auth.oauth2.AwsSecurityCredentialsSupplier
import com.google.auth.oauth2.ExternalAccountSupplierContext
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain

/**
 * Google workload identity federation from the ECS task role, replacing the Worker's
 * Cloudflare Access assertion broker. `GCP_FINOPS_WIF_CREDENTIAL_CONFIG_JSON` is the
 * external-account (AWS source) credential configuration produced by
 * `gcloud iam workload-identity-pools create-cred-config --aws`; it contains no secret.
 *
 * google-auth-library's built-in AWS source only reads environment keys or EC2 IMDS,
 * which ECS Fargate does not provide, so the AWS credentials are supplied from the AWS SDK
 * default chain (container credentials endpoint) through [AwsSecurityCredentialsSupplier].
 */
object GcpAwsWorkloadIdentity {
    private const val CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform"
    private val AUDIENCE = Regex(
        "^//iam\\.googleapis\\.com/projects/[0-9]+/locations/global/workloadIdentityPools/[a-z0-9-]+/providers/[a-z0-9-]+$",
    )
    private val IMPERSONATION_URL = Regex(
        "^https://iamcredentials\\.googleapis\\.com/v1/projects/-/serviceAccounts/" +
            "[a-z][a-z0-9-]{4,61}[a-z0-9]@[a-z][a-z0-9-]{4,61}[a-z0-9]\\.iam\\.gserviceaccount\\.com:generateAccessToken$",
    )

    fun credentials(configJson: String): GoogleCredentials {
        require(configJson.length <= 16_384) { "GCP WIF credential configuration is too large" }
        val config = Json.parseToJsonElement(configJson) as? JsonObject
            ?: throw IllegalArgumentException("GCP WIF credential configuration must be a JSON object")
        fun string(name: String): String? = (config[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(string("type") == "external_account") { "GCP WIF credential configuration must be an external_account" }
        val audience = requireNotNull(string("audience")) { "GCP WIF audience is required" }
        require(AUDIENCE.matches(audience)) { "invalid GCP WIF provider audience" }
        val subjectTokenType = requireNotNull(string("subject_token_type")) { "GCP WIF subject token type is required" }
        require(subjectTokenType == "urn:ietf:params:aws:token-type:aws4_request") {
            "GCP WIF credential configuration must use the AWS subject token type"
        }
        val tokenUrl = string("token_url") ?: "https://sts.googleapis.com/v1/token"
        require(tokenUrl == "https://sts.googleapis.com/v1/token") { "unexpected GCP STS token URL" }
        val impersonationUrl = requireNotNull(string("service_account_impersonation_url")) {
            "GCP WIF service account impersonation is required"
        }
        require(IMPERSONATION_URL.matches(impersonationUrl)) { "invalid GCP WIF service account impersonation URL" }
        (config["credential_source"] as? JsonObject)?.let { source ->
            val environmentId = (source["environment_id"] as? JsonPrimitive)?.content.orEmpty()
            require(environmentId.startsWith("aws")) { "GCP WIF credential source must be AWS" }
        }
        return AwsCredentials.newBuilder()
            .setAudience(audience)
            .setSubjectTokenType(subjectTokenType)
            .setTokenUrl(tokenUrl)
            .setServiceAccountImpersonationUrl(impersonationUrl)
            .setAwsSecurityCredentialsSupplier(TaskRoleCredentialsSupplier())
            .setScopes(listOf(CLOUD_PLATFORM))
            .build()
    }

    /** Returns a fresh access token provider for [GcpBillingConnector]. */
    fun accessTokenProvider(configJson: String): () -> String {
        val credentials = credentials(configJson)
        return {
            credentials.refreshIfExpired()
            requireNotNull(credentials.accessToken?.tokenValue) { "GCP workload identity returned no access token" }
        }
    }

    private class TaskRoleCredentialsSupplier : AwsSecurityCredentialsSupplier {
        @Transient
        private var provider: DefaultCredentialsProvider? = null

        override fun getRegion(context: ExternalAccountSupplierContext): String =
            System.getenv("AWS_REGION")?.takeIf(String::isNotBlank) ?: DefaultAwsRegionProviderChain().region.id()

        override fun getCredentials(context: ExternalAccountSupplierContext): AwsSecurityCredentials {
            val resolved = synchronized(this) {
                provider ?: DefaultCredentialsProvider.builder().build().also { provider = it }
            }.resolveCredentials()
            val sessionToken = (resolved as? AwsSessionCredentials)?.sessionToken()
            return AwsSecurityCredentials(resolved.accessKeyId(), resolved.secretAccessKey(), sessionToken)
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }
}

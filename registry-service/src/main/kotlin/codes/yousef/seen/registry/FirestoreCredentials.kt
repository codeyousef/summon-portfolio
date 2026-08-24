package codes.yousef.seen.registry

import com.google.auth.oauth2.ExternalAccountCredentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.util.Base64

internal object FirestoreCredentials {
    private const val MAX_ENCODED_BYTES = 1_048_576

    fun fromEnvironment(environment: (String) -> String? = System::getenv): GoogleCredentials? {
        val external = environment("FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64")?.trim()?.takeIf(String::isNotEmpty)
        val serviceAccount = environment("FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64")?.trim()?.takeIf(String::isNotEmpty)
        require(external == null || serviceAccount == null) {
            "Configure exactly one Firestore credential; remove the service-account key after enabling WIF"
        }
        val encoded = external ?: serviceAccount ?: return null
        require(encoded.length <= MAX_ENCODED_BYTES) { "Firestore credential is too large" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Firestore credential is not valid base64", it) }
        require(decoded.isNotEmpty() && decoded.size <= MAX_ENCODED_BYTES) { "Firestore credential is empty or too large" }
        return try {
            val credentials = decoded.inputStream().use(GoogleCredentials::fromStream)
            when {
                external != null -> require(credentials is ExternalAccountCredentials) {
                    "FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64 must contain an external-account credential"
                }
                else -> require(credentials is ServiceAccountCredentials) {
                    "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 must contain a service-account credential"
                }
            }
            credentials
        } finally {
            decoded.fill(0)
        }
    }
}

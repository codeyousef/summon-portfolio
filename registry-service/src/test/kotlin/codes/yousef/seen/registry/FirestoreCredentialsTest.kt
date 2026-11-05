package codes.yousef.seen.registry

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FirestoreCredentialsTest {
    @Test
    fun `absent explicit credential preserves application default credentials`() {
        assertNull(FirestoreCredentials.fromEnvironment { null })
    }

    @Test
    fun `mixed service account and WIF credentials fail closed`() {
        val environment = mapOf(
            "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64" to "service",
            "FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64" to "external",
        )

        assertFailsWith<IllegalArgumentException> {
            FirestoreCredentials.fromEnvironment(environment::get)
        }
    }

    @Test
    fun `user credentials cannot be smuggled through the service account binding`() {
        val authorizedUser = """{
            "type":"authorized_user",
            "client_id":"client-id",
            "client_secret":"client-secret",
            "refresh_token":"refresh-token"
        }""".trimIndent()
        val environment = mapOf(
            "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64" to Base64.getEncoder().encodeToString(authorizedUser.toByteArray()),
        )

        assertFailsWith<IllegalArgumentException> {
            FirestoreCredentials.fromEnvironment(environment::get)
        }
    }

    @Test
    fun `malformed base64 fails without falling back to ambient credentials`() {
        val environment = mapOf("FIRESTORE_EXTERNAL_ACCOUNT_JSON_BASE64" to "not-base64!")

        assertFailsWith<IllegalArgumentException> {
            FirestoreCredentials.fromEnvironment(environment::get)
        }
    }
}

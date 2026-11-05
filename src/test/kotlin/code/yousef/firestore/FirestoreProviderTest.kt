package code.yousef.firestore

import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirestoreProviderTest {
    @Test
    fun `decodes an in-memory service account for the configured project`() {
        val credentials = FirestoreProvider.decodeServiceAccountCredentials(
            encoded = serviceAccount("portfolio-476219"),
            expectedProjectId = "portfolio-476219",
        )

        assertEquals("portfolio-476219", credentials.projectId)
        assertEquals("portfolio-runtime@portfolio-476219.iam.gserviceaccount.com", credentials.clientEmail)
    }

    @Test
    fun `rejects malformed non-service-account and cross-project credentials without echoing them`() {
        val malformed = assertFailsWith<IllegalArgumentException> {
            FirestoreProvider.decodeServiceAccountCredentials("not base64!", "portfolio-476219")
        }
        assertTrue(malformed.message.orEmpty().contains("strict Base64"))

        val crossProjectSecret = serviceAccount("different-project")
        val crossProject = assertFailsWith<IllegalArgumentException> {
            FirestoreProvider.decodeServiceAccountCredentials(crossProjectSecret, "portfolio-476219")
        }
        assertTrue(crossProject.message.orEmpty().contains("project_id"))
        assertTrue(!crossProject.message.orEmpty().contains(crossProjectSecret))
    }

    private fun serviceAccount(projectId: String): String {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val privateKey = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(generator.generateKeyPair().private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\\n" +
            privateKey.replace("\n", "\\n") +
            "\\n-----END PRIVATE KEY-----\\n"
        val json = """
            {
              "type": "service_account",
              "project_id": "$projectId",
              "private_key_id": "test-key",
              "private_key": "$pem",
              "client_email": "portfolio-runtime@$projectId.iam.gserviceaccount.com",
              "client_id": "1234567890",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token",
              "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
              "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/test"
            }
        """.trimIndent()
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }
}

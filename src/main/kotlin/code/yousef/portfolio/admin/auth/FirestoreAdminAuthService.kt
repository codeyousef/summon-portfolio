package code.yousef.portfolio.admin.auth

import com.google.cloud.firestore.Firestore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Firestore-backed admin authentication service.
 * Credentials persist across deployments in Firestore.
 */
class FirestoreAdminAuthService(
    private val firestore: Firestore,
    private val collectionName: String = "admin_settings",
    private val documentId: String = "credentials"
) : AdminAuthProvider {
    private val log = LoggerFactory.getLogger(FirestoreAdminAuthService::class.java)
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedCredentials: StoredCredentials = initialize()

    private fun initialize(): StoredCredentials {
        log.info("Initializing FirestoreAdminAuthService with collection=$collectionName, doc=$documentId")
        
        val docRef = firestore.collection(collectionName).document(documentId)
        val snapshot = docRef.get().get()
        
        return if (snapshot.exists()) {
            val data = snapshot.data
            if (data != null) {
                try {
                    val creds = StoredCredentials(
                        username = data["username"] as? String ?: "admin",
                        passwordHash = data["passwordHash"] as? String ?: "",
                        salt = data["salt"] as? String ?: "",
                        mustChange = data["mustChange"] as? Boolean ?: true
                    )
                    log.info("Loaded credentials from Firestore for user '${creds.username}', mustChange=${creds.mustChange}")
                    creds
                } catch (e: Exception) {
                    log.error("Failed to parse Firestore credentials, creating defaults", e)
                    createDefaultCredentials()
                }
            } else {
                log.warn("Firestore credentials document exists but has no data, creating defaults")
                createDefaultCredentials()
            }
        } else {
            log.warn("No credentials found in Firestore, creating defaults")
            createDefaultCredentials()
        }
    }

    private fun createDefaultCredentials(): StoredCredentials {
        log.warn("Creating DEFAULT admin credentials (admin/admin) - user must change password on first login")
        val salt = generateSalt()
        val default = StoredCredentials(
            username = "admin",
            passwordHash = hashPassword("admin", salt),
            salt = salt,
            mustChange = true
        )
        persist(default)
        return default
    }

    override fun authenticate(username: String, password: String): AdminAuthProvider.AuthResult {
        val creds = cachedCredentials
        val attemptedHash = hashPassword(password, creds.salt)
        return if (creds.username == username && creds.passwordHash == attemptedHash) {
            log.info("Successful authentication for user '$username', mustChangePassword=${creds.mustChange}")
            AdminAuthProvider.AuthResult.Success(creds.mustChange)
        } else {
            log.warn("Failed authentication attempt for user '$username'")
            AdminAuthProvider.AuthResult.Invalid
        }
    }

    override fun mustChangePassword(): Boolean = cachedCredentials.mustChange

    override fun currentUsername(): String = cachedCredentials.username

    override fun updateCredentials(username: String, password: String) {
        log.info("Updating credentials for user '$username'")
        val salt = generateSalt()
        val updated = StoredCredentials(
            username = username,
            passwordHash = hashPassword(password, salt),
            salt = salt,
            mustChange = false
        )
        cachedCredentials = updated
        persist(updated)
        log.info("Credentials updated and persisted to Firestore")
    }

    private fun persist(credentials: StoredCredentials) {
        val docRef = firestore.collection(collectionName).document(documentId)
        val data = mapOf(
            "username" to credentials.username,
            "passwordHash" to credentials.passwordHash,
            "salt" to credentials.salt,
            "mustChange" to credentials.mustChange
        )
        docRef.set(data).get() // Blocking write
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hashPassword(password: String, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.getDecoder().decode(saltBase64))
        digest.update(password.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    @Serializable
    private data class StoredCredentials(
        val username: String,
        val passwordHash: String,
        val salt: String,
        val mustChange: Boolean
    )
}

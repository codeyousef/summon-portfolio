package code.yousef.portfolio.admin.auth

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.sourcePortfolioFirestoreStore
import com.google.cloud.firestore.Firestore
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Firestore-backed admin authentication service.
 * Credentials persist across deployments in Firestore.
 */
class FirestoreAdminAuthService(
    private val store: PortfolioFirestoreStore,
    private val collectionName: String = PortfolioFirestoreCollections.ADMIN_SETTINGS,
    private val documentId: String = "credentials",
    private val allowBootstrapCredentials: Boolean = false,
) : AdminAuthProvider {
    constructor(
        firestore: Firestore,
        collectionName: String = PortfolioFirestoreCollections.ADMIN_SETTINGS,
        documentId: String = "credentials",
        allowBootstrapCredentials: Boolean = false,
    ) : this(sourcePortfolioFirestoreStore(firestore), collectionName, documentId, allowBootstrapCredentials)

    private val log = LoggerFactory.getLogger(FirestoreAdminAuthService::class.java)
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedCredentials: StoredCredentials? = null

    private fun currentCredentials(): StoredCredentials {
        cachedCredentials?.let { return it }
        return synchronized(this) {
            cachedCredentials ?: initialize().also { cachedCredentials = it }
        }
    }

    private fun initialize(): StoredCredentials {
        log.info("Initializing FirestoreAdminAuthService with collection=$collectionName, doc=$documentId")

        val document = try {
            store.get(collectionName, documentId)
        } catch (failure: Exception) {
            log.error("Failed to read Firestore admin credentials; refusing to initialize", failure)
            throw IllegalStateException("Unable to load Firestore admin credentials", failure)
        }
        val data = document?.data
        val passwordHash = data?.get("passwordHash") as? String
        val salt = data?.get("salt") as? String
        if (!passwordHash.isNullOrEmpty() && !salt.isNullOrEmpty()) {
            return StoredCredentials(
                username = data["username"] as? String ?: "admin",
                passwordHash = passwordHash,
                salt = salt,
                mustChange = data["mustChange"] as? Boolean ?: true,
            ).also { credentials ->
                log.info(
                    "Loaded Firestore admin credentials for user '{}', mustChange={}",
                    credentials.username,
                    credentials.mustChange,
                )
            }
        }

        check(store.writeMode == FirestoreWriteMode.SOURCE) {
            "Admin credentials are missing or malformed in ${store.writeMode.environmentValue} mode; " +
                "complete and reconcile the Firestore backfill before startup"
        }
        check(allowBootstrapCredentials) {
            "Admin credentials are missing or malformed; run an explicit one-instance bootstrap instead of " +
                "creating default credentials during scalable startup"
        }
        log.warn("No valid credentials found in source Firestore; creating bootstrap credentials")
        return createDefaultCredentials()
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
        val creds = currentCredentials()
        val attemptedHash = hashPassword(password, creds.salt)
        return if (creds.username == username && creds.passwordHash == attemptedHash) {
            log.info("Successful authentication for user '$username', mustChangePassword=${creds.mustChange}")
            AdminAuthProvider.AuthResult.Success(creds.mustChange)
        } else {
            log.warn("Failed authentication attempt for user '$username'")
            AdminAuthProvider.AuthResult.Invalid
        }
    }

    override fun mustChangePassword(): Boolean = currentCredentials().mustChange

    override fun currentUsername(): String = currentCredentials().username

    override fun updateCredentials(username: String, password: String) {
        log.info("Updating credentials for user '$username'")
        val salt = generateSalt()
        val updated = StoredCredentials(
            username = username,
            passwordHash = hashPassword(password, salt),
            salt = salt,
            mustChange = false
        )
        synchronized(this) {
            persist(updated)
            cachedCredentials = updated
        }
        log.info("Credentials updated and persisted to Firestore")
    }

    private fun persist(credentials: StoredCredentials) {
        try {
            val data = mapOf(
                "username" to credentials.username,
                "passwordHash" to credentials.passwordHash,
                "salt" to credentials.salt,
                "mustChange" to credentials.mustChange
            )
            store.upsert(collectionName, documentId, data)
            log.info(
                "Credentials persisted to Firestore: collection={}, doc={}, username={}, mustChange={}",
                collectionName,
                documentId,
                credentials.username,
                credentials.mustChange,
            )
        } catch (e: Exception) {
            log.error("FAILED to persist credentials to Firestore!", e)
            throw e // Re-throw so caller knows it failed
        }
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

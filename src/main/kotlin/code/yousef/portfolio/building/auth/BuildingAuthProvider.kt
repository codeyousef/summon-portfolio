package code.yousef.portfolio.building.auth

import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.sourcePortfolioFirestoreStore
import com.google.cloud.firestore.Firestore
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Multi-user authentication provider for building management.
 * Stores multiple users in Firestore collection "building_users".
 * 
 * Initial users (seeded on first run):
 * - waleed / waleed (must change password)
 * - raghad / raghad (must change password)
 * - abdu / abdu (must change password)
 * - admin / admin (must change password)
 */
class BuildingAuthProvider(
    private val store: PortfolioFirestoreStore,
    private val collectionName: String = PortfolioFirestoreCollections.BUILDING_USERS,
    seedOnInit: Boolean = false,
) {
    constructor(
        firestore: Firestore,
        collectionName: String = PortfolioFirestoreCollections.BUILDING_USERS,
        seedOnInit: Boolean = false,
    ) : this(sourcePortfolioFirestoreStore(firestore), collectionName, seedOnInit)

    private val log = LoggerFactory.getLogger(BuildingAuthProvider::class.java)
    private val secureRandom = SecureRandom()

    init {
        if (seedOnInit) ensureSeedUsers()
    }

    private fun ensureSeedUsers() {
        val seedUsers = listOf(
            SeedUser("waleed", "waleed"),
            SeedUser("raghad", "raghad"),
            SeedUser("abdu", "abdu"),
            SeedUser("admin", "admin")
        )

        seedUsers.forEach { seedUser ->
            try {
                val document = store.get(collectionName, seedUser.username)

                if (document == null) {
                    check(store.writeMode == FirestoreWriteMode.SOURCE) {
                        "Building auth user '${seedUser.username}' is missing in " +
                            "${store.writeMode.environmentValue} mode; complete and reconcile " +
                            "the Firestore backfill before startup"
                    }
                    log.info("Creating seed user: ${seedUser.username}")
                    val salt = generateSalt()
                    val data = mapOf(
                        "username" to seedUser.username,
                        "passwordHash" to hashPassword(seedUser.password, salt),
                        "salt" to salt,
                        "mustChangePassword" to true
                    )
                    store.upsert(collectionName, seedUser.username, data)
                    log.info("Seed user created: ${seedUser.username}")
                } else {
                    log.info("Seed user already exists: ${seedUser.username}")
                }
            } catch (e: Exception) {
                log.error("Failed to seed user ${seedUser.username}", e)
                throw IllegalStateException(
                    "Unable to establish required building auth user '${seedUser.username}'",
                    e,
                )
            }
        }
    }

    fun authenticate(username: String, password: String): AuthResult {
        return try {
            val document = store.get(collectionName, username)

            if (document == null) {
                log.warn("Authentication failed: user '$username' not found")
                return AuthResult.Invalid
            }

            val data = document.data
            val storedHash = data["passwordHash"] as? String ?: return AuthResult.Invalid
            val salt = data["salt"] as? String ?: return AuthResult.Invalid
            val mustChangePassword = data["mustChangePassword"] as? Boolean ?: true

            val attemptedHash = hashPassword(password, salt)
            if (storedHash == attemptedHash) {
                log.info("Authentication successful for user '$username', mustChangePassword=$mustChangePassword")
                AuthResult.Success(mustChangePassword)
            } else {
                log.warn("Authentication failed: invalid password for user '$username'")
                AuthResult.Invalid
            }
        } catch (e: Exception) {
            log.error("Authentication error for user '$username'", e)
            AuthResult.Invalid
        }
    }

    fun mustChangePassword(username: String): Boolean {
        return try {
            store.get(collectionName, username)?.data?.get("mustChangePassword") as? Boolean ?: true
        } catch (e: Exception) {
            log.error("Error checking mustChangePassword for user '$username'", e)
            true
        }
    }

    fun updatePassword(username: String, newPassword: String) {
        try {
            check(store.get(collectionName, username) != null) {
                "Cannot update password: user '$username' not found"
            }

            val salt = generateSalt()
            val data = mapOf(
                "username" to username,
                "passwordHash" to hashPassword(newPassword, salt),
                "salt" to salt,
                "mustChangePassword" to false
            )
            store.upsert(collectionName, username, data)
            log.info("Password updated for user '$username'")
        } catch (e: Exception) {
            log.error("Failed to update password for user '$username'", e)
            throw e
        }
    }

    fun listUsers(): List<String> {
        return try {
            store.list(collectionName).mapNotNull { document ->
                document.data["username"] as? String
            }
        } catch (e: Exception) {
            log.error("Failed to list users", e)
            emptyList()
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

    sealed class AuthResult {
        object Invalid : AuthResult()
        data class Success(val mustChangePassword: Boolean) : AuthResult()
    }

    private data class SeedUser(val username: String, val password: String)
}

/**
 * Session data for building management users
 */
data class BuildingSession(
    val username: String,
    val mustChangePassword: Boolean
)

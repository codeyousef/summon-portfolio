package code.yousef.portfolio.building.auth

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
 * - admin / admin (must change password)
 */
class BuildingAuthProvider(
    private val firestore: Firestore,
    private val collectionName: String = "building_users"
) {
    private val log = LoggerFactory.getLogger(BuildingAuthProvider::class.java)
    private val secureRandom = SecureRandom()

    init {
        ensureSeedUsers()
    }

    private fun ensureSeedUsers() {
        val seedUsers = listOf(
            SeedUser("waleed", "waleed"),
            SeedUser("raghad", "raghad"),
            SeedUser("admin", "admin")
        )

        seedUsers.forEach { seedUser ->
            try {
                val docRef = firestore.collection(collectionName).document(seedUser.username)
                val snapshot = docRef.get().get()
                
                if (!snapshot.exists()) {
                    log.info("Creating seed user: ${seedUser.username}")
                    val salt = generateSalt()
                    val data = mapOf(
                        "username" to seedUser.username,
                        "passwordHash" to hashPassword(seedUser.password, salt),
                        "salt" to salt,
                        "mustChangePassword" to true
                    )
                    docRef.set(data).get()
                    log.info("Seed user created: ${seedUser.username}")
                } else {
                    log.info("Seed user already exists: ${seedUser.username}")
                }
            } catch (e: Exception) {
                log.error("Failed to seed user ${seedUser.username}", e)
            }
        }
    }

    fun authenticate(username: String, password: String): AuthResult {
        return try {
            val docRef = firestore.collection(collectionName).document(username)
            val snapshot = docRef.get().get()
            
            if (!snapshot.exists()) {
                log.warn("Authentication failed: user '$username' not found")
                return AuthResult.Invalid
            }

            val data = snapshot.data ?: return AuthResult.Invalid
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
            val docRef = firestore.collection(collectionName).document(username)
            val snapshot = docRef.get().get()
            snapshot.data?.get("mustChangePassword") as? Boolean ?: true
        } catch (e: Exception) {
            log.error("Error checking mustChangePassword for user '$username'", e)
            true
        }
    }

    fun updatePassword(username: String, newPassword: String) {
        try {
            val docRef = firestore.collection(collectionName).document(username)
            val snapshot = docRef.get().get()
            
            if (!snapshot.exists()) {
                log.error("Cannot update password: user '$username' not found")
                return
            }

            val salt = generateSalt()
            val data = mapOf(
                "username" to username,
                "passwordHash" to hashPassword(newPassword, salt),
                "salt" to salt,
                "mustChangePassword" to false
            )
            docRef.set(data).get()
            log.info("Password updated for user '$username'")
        } catch (e: Exception) {
            log.error("Failed to update password for user '$username'", e)
            throw e
        }
    }

    fun listUsers(): List<String> {
        return try {
            firestore.collection(collectionName).get().get().documents.mapNotNull { doc ->
                doc.getString("username")
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

package code.yousef.portfolio.admin.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AdminAuthService(
    private val credentialsPath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) {
    private val log = LoggerFactory.getLogger(AdminAuthService::class.java)
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedCredentials: StoredCredentials = initialize()

    private fun initialize(): StoredCredentials {
        log.info("Initializing AdminAuthService with credentials path: ${credentialsPath.toAbsolutePath()}")
        credentialsPath.parent?.let { parent ->
            if (!parent.exists()) {
                log.info("Creating parent directory: ${parent.toAbsolutePath()}")
                parent.createDirectories()
            }
        }
        return if (credentialsPath.exists()) {
            log.info("Found existing credentials file at: ${credentialsPath.toAbsolutePath()}")
            runCatching {
                val creds = json.decodeFromString<StoredCredentials>(credentialsPath.readText())
                log.info("Loaded credentials for user '${creds.username}', mustChange=${creds.mustChange}")
                creds
            }.getOrElse { e ->
                log.error("Failed to parse credentials file, creating defaults", e)
                createDefaultCredentials()
            }
        } else {
            log.warn("No credentials file found at ${credentialsPath.toAbsolutePath()}, creating defaults")
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

    fun authenticate(username: String, password: String): AuthResult {
        val creds = cachedCredentials
        val attemptedHash = hashPassword(password, creds.salt)
        return if (creds.username == username && creds.passwordHash == attemptedHash) {
            log.info("Successful authentication for user '${username}', mustChangePassword=${creds.mustChange}")
            AuthResult.Success(creds.mustChange)
        } else {
            log.warn("Failed authentication attempt for user '${username}'")
            AuthResult.Invalid
        }
    }

    fun mustChangePassword(): Boolean = cachedCredentials.mustChange

    fun currentUsername(): String = cachedCredentials.username

    fun updateCredentials(username: String, password: String) {
        log.info("Updating credentials for user '${username}'")
        val salt = generateSalt()
        val updated = StoredCredentials(
            username = username,
            passwordHash = hashPassword(password, salt),
            salt = salt,
            mustChange = false
        )
        cachedCredentials = updated
        persist(updated)
        log.info("Credentials updated and persisted successfully to ${credentialsPath.toAbsolutePath()}")
    }

    private fun persist(credentials: StoredCredentials) {
        synchronized(this) {
            credentialsPath.writeText(json.encodeToString(credentials))
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

    sealed class AuthResult {
        object Invalid : AuthResult()
        data class Success(val mustChangePassword: Boolean) : AuthResult()
    }
}

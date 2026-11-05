package code.yousef.portfolio.building.auth

import code.yousef.firestore.PortfolioFirestoreCollections
import code.yousef.firestore.PortfolioFirestoreStore
import code.yousef.firestore.sourcePortfolioFirestoreStore
import com.google.cloud.firestore.Firestore
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

/**
 * Service for managing password reset tokens.
 * Tokens are stored in Firestore and expire after 24 hours.
 * Admin generates a reset link, shares it via secure channel (WhatsApp, Signal, etc.).
 */
class PasswordResetService(
    private val store: PortfolioFirestoreStore,
    private val authProvider: BuildingAuthProvider,
    private val collectionName: String = PortfolioFirestoreCollections.BUILDING_PASSWORD_RESET_TOKENS,
) {
    constructor(
        firestore: Firestore,
        authProvider: BuildingAuthProvider,
        collectionName: String = PortfolioFirestoreCollections.BUILDING_PASSWORD_RESET_TOKENS,
    ) : this(sourcePortfolioFirestoreStore(firestore), authProvider, collectionName)

    private val log = LoggerFactory.getLogger(PasswordResetService::class.java)
    private val secureRandom = SecureRandom()
    
    companion object {
        const val TOKEN_VALIDITY_HOURS = 24L
        const val TOKEN_LENGTH = 64
    }
    
    /**
     * Generates a secure random token.
     */
    private fun generateToken(): String {
        val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..TOKEN_LENGTH)
            .map { charPool[secureRandom.nextInt(charPool.length)] }
            .joinToString("")
    }
    
    /**
     * Creates a password reset token for a user.
     * @param username The username to create the reset token for.
     * @return The generated token, or null if user doesn't exist.
     */
    fun createResetToken(username: String): String? {
        // Verify user exists
        val users = authProvider.listUsers()
        if (username !in users) {
            log.warn("Cannot create reset token: user '$username' does not exist")
            return null
        }
        
        try {
            // Invalidate before publishing the replacement token. Any failure is fail-closed.
            invalidateTokensForUser(username)

            val token = generateToken()
            val expireTime = System.currentTimeMillis() + (TOKEN_VALIDITY_HOURS * 60 * 60 * 1000)
            val data = mapOf(
                "token" to token,
                "username" to username,
                "expireTime" to expireTime,
                "used" to false,
                "createdAt" to System.currentTimeMillis()
            )
            store.upsert(collectionName, token, data)
            log.info("Created password reset token for user '$username', expires at ${Date(expireTime)}")
            return token
        } catch (e: Exception) {
            log.error("Failed to create reset token for user '$username'", e)
            return null
        }
    }
    
    /**
     * Validates a token and returns the associated username if valid.
     * Does NOT consume the token - call consumeToken after password update succeeds.
     * @param token The reset token to validate.
     * @return The username if token is valid, null otherwise.
     */
    fun validateToken(token: String): String? {
        return try {
            val document = store.get(collectionName, token)

            if (document == null) {
                log.warn("Token validation failed: token not found")
                return null
            }

            val data = document.data
            val used = data["used"] as? Boolean ?: true
            val expireTime = (data["expireTime"] as? Number)?.toLong() ?: 0
            val username = data["username"] as? String
            
            when {
                used -> {
                    log.warn("Token validation failed: token already used")
                    null
                }
                System.currentTimeMillis() > expireTime -> {
                    log.warn("Token validation failed: token expired")
                    null
                }
                username == null -> {
                    log.warn("Token validation failed: no username in token data")
                    null
                }
                else -> {
                    log.info("Token validated successfully for user '$username'")
                    username
                }
            }
        } catch (e: Exception) {
            log.error("Token validation error", e)
            null
        }
    }
    
    /**
     * Consumes (invalidates) a token after successful password reset.
     * @param token The token to consume.
     */
    fun consumeToken(token: String) {
        try {
            check(store.get(collectionName, token) != null) { "Reset token does not exist" }
            store.merge(collectionName, token, mapOf("used" to true))
            log.info("Token consumed successfully")
        } catch (e: Exception) {
            log.error("Failed to consume token", e)
            throw e
        }
    }
    
    /**
     * Invalidates all existing tokens for a user.
     */
    private fun invalidateTokensForUser(username: String) {
        try {
            val tokens = store.whereEqualTo(
                collection = collectionName,
                filters = mapOf("username" to username, "used" to false),
            )

            tokens.forEach { document ->
                store.merge(collectionName, document.id, mapOf("used" to true))
            }

            if (tokens.isNotEmpty()) {
                log.info("Invalidated ${tokens.size} existing tokens for user '$username'")
            }
        } catch (e: Exception) {
            log.error("Failed to invalidate existing tokens for user '$username'", e)
            throw e
        }
    }
    
    /**
     * Cleans up expired tokens (can be called periodically).
     */
    fun cleanupExpiredTokens() {
        try {
            val expired = store.whereLessThan(
                collection = collectionName,
                field = "expireTime",
                value = System.currentTimeMillis(),
            )

            expired.forEach { document ->
                store.delete(collectionName, document.id)
            }

            if (expired.isNotEmpty()) {
                log.info("Cleaned up ${expired.size} expired tokens")
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup expired tokens", e)
        }
    }
}

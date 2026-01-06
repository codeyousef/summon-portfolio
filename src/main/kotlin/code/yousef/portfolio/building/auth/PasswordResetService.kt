package code.yousef.portfolio.building.auth

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
    private val firestore: Firestore,
    private val authProvider: BuildingAuthProvider,
    private val collectionName: String = "building_password_reset_tokens"
) {
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
        
        // Invalidate any existing tokens for this user
        invalidateTokensForUser(username)
        
        val token = generateToken()
        val expireTime = System.currentTimeMillis() + (TOKEN_VALIDITY_HOURS * 60 * 60 * 1000)
        
        try {
            val data = mapOf(
                "token" to token,
                "username" to username,
                "expireTime" to expireTime,
                "used" to false,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection(collectionName).document(token).set(data).get()
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
            val docRef = firestore.collection(collectionName).document(token)
            val snapshot = docRef.get().get()
            
            if (!snapshot.exists()) {
                log.warn("Token validation failed: token not found")
                return null
            }
            
            val data = snapshot.data ?: return null
            val used = data["used"] as? Boolean ?: true
            val expireTime = data["expireTime"] as? Long ?: 0
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
            val docRef = firestore.collection(collectionName).document(token)
            docRef.update("used", true).get()
            log.info("Token consumed successfully")
        } catch (e: Exception) {
            log.error("Failed to consume token", e)
        }
    }
    
    /**
     * Invalidates all existing tokens for a user.
     */
    private fun invalidateTokensForUser(username: String) {
        try {
            val tokens = firestore.collection(collectionName)
                .whereEqualTo("username", username)
                .whereEqualTo("used", false)
                .get().get()
            
            tokens.documents.forEach { doc ->
                doc.reference.update("used", true).get()
            }
            
            if (tokens.documents.isNotEmpty()) {
                log.info("Invalidated ${tokens.documents.size} existing tokens for user '$username'")
            }
        } catch (e: Exception) {
            log.error("Failed to invalidate existing tokens for user '$username'", e)
        }
    }
    
    /**
     * Cleans up expired tokens (can be called periodically).
     */
    fun cleanupExpiredTokens() {
        try {
            val expired = firestore.collection(collectionName)
                .whereLessThan("expireTime", System.currentTimeMillis())
                .get().get()
            
            expired.documents.forEach { doc ->
                doc.reference.delete().get()
            }
            
            if (expired.documents.isNotEmpty()) {
                log.info("Cleaned up ${expired.documents.size} expired tokens")
            }
        } catch (e: Exception) {
            log.error("Failed to cleanup expired tokens", e)
        }
    }
}

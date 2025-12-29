package code.yousef.portfolio.admin.auth

/**
 * Interface for admin authentication services.
 * Allows swapping between file-based (local dev) and Firestore-based (production) implementations.
 */
interface AdminAuthProvider {
    fun authenticate(username: String, password: String): AuthResult
    fun mustChangePassword(): Boolean
    fun currentUsername(): String
    fun updateCredentials(username: String, password: String)

    sealed class AuthResult {
        object Invalid : AuthResult()
        data class Success(val mustChangePassword: Boolean) : AuthResult()
    }
}

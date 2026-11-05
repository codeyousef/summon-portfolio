package code.yousef.firestore

import code.yousef.config.AppConfig
import code.yousef.config.DEFAULT_FIRESTORE_DATABASE_ID
import code.yousef.config.FirestoreWriteMode
import code.yousef.firestore.migration.FirestoreMutationBackend
import code.yousef.firestore.migration.MutationCoordinator
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.io.ByteArrayInputStream
import java.util.Base64

object FirestoreProvider {
    fun create(config: AppConfig): Firestore {
        require(config.firestoreWriteMode == FirestoreWriteMode.SOURCE) {
            "Dual and target Firestore modes require createDatabases() so replication cannot be skipped"
        }
        val databaseId = when (config.firestoreWriteMode) {
            FirestoreWriteMode.SOURCE -> DEFAULT_FIRESTORE_DATABASE_ID
            FirestoreWriteMode.DUAL, FirestoreWriteMode.TARGET -> error("Validated above")
        }
        return createDatabase(config, databaseId)
    }

    fun createDatabases(config: AppConfig): FirestoreDatabases {
        val source = createDatabase(config, DEFAULT_FIRESTORE_DATABASE_ID)
        val target = if (config.firestoreWriteMode != FirestoreWriteMode.SOURCE) {
            createDatabase(config, config.firestoreDatabaseId)
        } else {
            null
        }
        return FirestoreDatabases(
            writeMode = config.firestoreWriteMode,
            source = source,
            target = target,
            migrationProofId = config.firestoreMigrationProofId,
        )
    }

    private fun createDatabase(config: AppConfig, databaseId: String): Firestore {
        val builder = FirestoreOptions.newBuilder()
            .setProjectId(config.projectId)
            .setDatabaseId(databaseId)
        if (config.emulatorHost != null) {
            builder.setEmulatorHost(config.emulatorHost)
        } else {
            val credentials = config.firestoreServiceAccountJsonBase64?.let { encoded ->
                decodeServiceAccountCredentials(encoded, config.projectId)
            } ?: runCatching { GoogleCredentials.getApplicationDefault() }
                .getOrElse { throw IllegalStateException("Unable to load ADC credentials", it) }
            builder.setCredentials(credentials)
        }
        return builder.build().service
    }

    internal fun decodeServiceAccountCredentials(
        encoded: String,
        expectedProjectId: String,
    ): ServiceAccountCredentials {
        require(encoded.length <= MAX_SERVICE_ACCOUNT_BASE64_CHARS) {
            "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 exceeds the supported size"
        }
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException(
                "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 is not valid strict Base64",
                failure,
            )
        }
        try {
            require(decoded.size <= MAX_SERVICE_ACCOUNT_JSON_BYTES) {
                "Decoded Firestore service-account JSON exceeds the supported size"
            }
            val credentials = try {
                ByteArrayInputStream(decoded).use(GoogleCredentials::fromStream)
            } catch (failure: Exception) {
                throw IllegalArgumentException(
                    "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 does not contain valid Google credentials JSON",
                    failure,
                )
            }
            require(credentials is ServiceAccountCredentials) {
                "FIRESTORE_SERVICE_ACCOUNT_JSON_BASE64 must contain service-account credentials"
            }
            require(credentials.projectId == expectedProjectId) {
                "Firestore service-account project_id must match GOOGLE_CLOUD_PROJECT"
            }
            return credentials
        } finally {
            decoded.fill(0)
        }
    }

    private const val MAX_SERVICE_ACCOUNT_JSON_BYTES = 65_536
    private const val MAX_SERVICE_ACCOUNT_BASE64_CHARS = 87_384
}

class FirestoreDatabases internal constructor(
    val writeMode: FirestoreWriteMode,
    val source: Firestore?,
    val target: Firestore?,
    private val migrationProofId: String?,
) : AutoCloseable {
    val authority: Firestore = when (writeMode) {
        FirestoreWriteMode.SOURCE, FirestoreWriteMode.DUAL -> requireNotNull(source)
        FirestoreWriteMode.TARGET -> requireNotNull(target)
    }

    fun mutationCoordinator(): MutationCoordinator = MutationCoordinator(
        writeMode = writeMode,
        source = source?.let(::FirestoreMutationBackend),
        target = target?.let(::FirestoreMutationBackend),
    )

    fun portfolioStore(
        mutationCoordinator: MutationCoordinator = mutationCoordinator(),
    ): PortfolioFirestoreStore = MigratingPortfolioFirestoreStore(
        authority = authority,
        mutationCoordinator = mutationCoordinator,
    )

    /** Verifies the external backfill before any auth service can initialize against it. */
    fun verifyPortfolioMigrationReady() {
        if (writeMode == FirestoreWriteMode.SOURCE) return
        PortfolioFirestoreMigrationGate(
            source = requireNotNull(source),
            target = requireNotNull(target),
            proofId = requireNotNull(migrationProofId),
        ).verify(writeMode)
    }

    override fun close() {
        var closeFailure: Exception? = null
        listOfNotNull(source, target).forEach { firestore ->
            try {
                firestore.close()
            } catch (failure: Exception) {
                closeFailure = closeFailure ?: failure
            }
        }
        closeFailure?.let { throw it }
    }
}

package codes.yousef.seen.registry

import java.nio.file.Files
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Clock

data class OfflineBootstrapConfig(
    val environment: String,
    val repositoryId: String,
    val rootPublicKeys: List<ByteArray>,
    val rootSigningKeysPkcs8Base64: List<String>,
    val targetsPublicKeys: List<ByteArray>,
    val targetsSigningKeysPkcs8Base64: List<String>,
    val onlineKeys: TufOnlineKeys,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OfflineBootstrapConfig {
            fun csv(name: String) = env[name].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            fun public(role: String) = requireNotNull(env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]) {
                "Missing online public key for $role"
            }.hexToBytes()
            return OfflineBootstrapConfig(
                environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
                repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
                rootPublicKeys = csv("REGISTRY_OFFLINE_ROOT_PUBLIC_KEYS_HEX").map(String::hexToBytes),
                rootSigningKeysPkcs8Base64 = csv("REGISTRY_OFFLINE_ROOT_SIGNING_KEYS_PKCS8_BASE64"),
                targetsPublicKeys = csv("REGISTRY_OFFLINE_TARGETS_PUBLIC_KEYS_HEX").map(String::hexToBytes),
                targetsSigningKeysPkcs8Base64 = csv("REGISTRY_OFFLINE_TARGETS_SIGNING_KEYS_PKCS8_BASE64"),
                onlineKeys = TufOnlineKeys(public(TufRole.RELEASES), public(TufRole.SECURITY), public(TufRole.SNAPSHOT), public(TufRole.TIMESTAMP)),
            ).also { require(it.environment == "development") { "Offline bootstrap is development-only" } }
        }
    }
}

data class OfflineTargetsRenewalConfig(
    val environment: String,
    val repositoryId: String,
    val targetsSigningKeysPkcs8Base64: List<String>,
    val onlineKeys: TufOnlineKeys,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OfflineTargetsRenewalConfig {
            fun csv(name: String) = env[name].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            fun public(role: String) = requireNotNull(env["REGISTRY_KMS_${role.uppercase()}_PUBLIC_KEY_HEX"]) {
                "Missing online public key for $role"
            }.hexToBytes()
            return OfflineTargetsRenewalConfig(
                environment = env["REGISTRY_ENVIRONMENT"] ?: "development",
                repositoryId = env["REGISTRY_REPOSITORY_ID"] ?: "seen-dev-registry-v1",
                targetsSigningKeysPkcs8Base64 = csv("REGISTRY_OFFLINE_TARGETS_SIGNING_KEYS_PKCS8_BASE64"),
                onlineKeys = TufOnlineKeys(public(TufRole.RELEASES), public(TufRole.SECURITY), public(TufRole.SNAPSHOT), public(TufRole.TIMESTAMP)),
            ).also { require(it.environment == "development") { "Offline targets renewal is development-only" } }
        }
    }
}

fun prepareOfflineBootstrap(outputDirectory: Path, config: OfflineBootstrapConfig, clock: Clock = Clock.systemUTC()): TufBootstrapResult {
    val rootSigners = config.rootSigningKeysPkcs8Base64.map(LocalEd25519Signer::fromPkcs8Base64)
    val targetSigners = config.targetsSigningKeysPkcs8Base64.map(LocalEd25519Signer::fromPkcs8Base64)
    try {
        return TufBootstrapper(
            storage = DirectoryMetadataStorage(outputDirectory),
            rootPublicKeys = config.rootPublicKeys,
            rootSigners = rootSigners,
            targetsPublicKeys = config.targetsPublicKeys,
            targetsSigners = targetSigners,
            online = config.onlineKeys,
            environment = config.environment,
            repositoryId = config.repositoryId,
            clock = clock,
        ).bootstrap()
    } finally {
        rootSigners.forEach(TufSigner::close)
        targetSigners.forEach(TufSigner::close)
    }
}

fun prepareOfflineTargetsRenewal(
    outputDirectory: Path,
    trustedRoot: ByteArray,
    currentTargets: ByteArray,
    config: OfflineTargetsRenewalConfig,
    clock: Clock = Clock.systemUTC(),
): TufTargetsRenewalResult {
    val signers = config.targetsSigningKeysPkcs8Base64.map(LocalEd25519Signer::fromPkcs8Base64)
    try {
        val result = TufTargetsRenewalPolicy(config.onlineKeys, config.environment, config.repositoryId, clock)
            .prepare(trustedRoot, currentTargets, signers)
        val storage = DirectoryMetadataStorage(outputDirectory)
        val filename = "${result.version}.targets.json"
        val existing = storage.getMetadata(filename)
        require(existing == null || existing.contentEquals(result.targets)) { "Existing $filename differs; refusing offline overwrite" }
        if (existing == null && !storage.putMetadataIfAbsent(filename, result.targets)) {
            require(storage.getMetadata(filename)?.contentEquals(result.targets) == true) {
                "Concurrent $filename creation differs; refusing offline overwrite"
            }
        }
        return result
    } finally {
        signers.forEach(TufSigner::close)
    }
}

private class DirectoryMetadataStorage(private val directory: Path) : RegistryObjectStorage {
    init { Files.createDirectories(directory) }
    override fun putMetadata(filename: String, bytes: ByteArray) {
        val destination = resolve(filename)
        val temporary = Files.createTempFile(directory, ".$filename.", ".tmp")
        try {
            Files.write(temporary, bytes)
            runCatching {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            }.recoverCatching {
                Files.move(temporary, destination)
            }.getOrThrow()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
    override fun putMetadataIfAbsent(filename: String, bytes: ByteArray): Boolean = try {
        Files.write(resolve(filename), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        true
    } catch (_: FileAlreadyExistsException) {
        false
    }
    override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean = synchronized(this) {
        val current = getMetadata(filename)
        val unchanged = if (expected == null) current == null else current?.contentEquals(expected) == true
        if (!unchanged) return@synchronized false
        putMetadata(filename, bytes)
        true
    }
    override fun getMetadata(filename: String): ByteArray? = resolve(filename).takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
    private fun resolve(filename: String): Path {
        require(filename.matches(Regex("^[A-Za-z0-9.-]+$"))) { "Invalid metadata filename" }
        return directory.resolve(filename).normalize().also { require(it.parent == directory.normalize()) }
    }
    override fun putQuarantine(uploadId: String, bytes: ByteArray) = unsupported()
    override fun getQuarantine(uploadId: String): ByteArray? = unsupported()
    override fun deleteQuarantine(uploadId: String) = unsupported()
    override fun putPublicBlob(digest: String, bytes: ByteArray) = unsupported()
    override fun getPublicBlob(digest: String): ByteArray? = unsupported()
    private fun unsupported(): Nothing = error("Offline bootstrap storage supports metadata only")
}

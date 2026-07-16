package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Supported source forges. Package content is never loaded as code or executed. */
enum class SourceForge(val wireName: String) {
    GITHUB("github"),
    GITLAB("gitlab");

    companion object {
        fun fromWireName(value: String): SourceForge = entries.firstOrNull { it.wireName == value }
            ?: throw SourceVerificationException(SourceVerificationFailure.UNSUPPORTED_FORGE)
    }
}

/** One regular file from an archive that already passed structural validation. */
data class SourceArchiveFile(
    val path: String,
    val bytes: ByteArray,
)

/**
 * Integration-neutral input to source verification.
 *
 * [declaredPackagePaths] is the authoritative regular-file set produced by the
 * archive/include validator. Requiring it to equal [archiveFiles] prevents this
 * layer from silently accepting an undeclared or omitted package file.
 */
data class SourceVerificationInput(
    val forge: SourceForge,
    val repositoryId: String,
    val canonicalRepositoryUrl: String,
    val installationIdentity: String,
    val requestedRef: String,
    val expectedCommit: String,
    val archiveSha256: String,
    val manifestBytes: ByteArray,
    val archiveFiles: List<SourceArchiveFile>,
    val declaredPackagePaths: Set<String>,
    val licenseSpdx: String,
)

data class ForgeInspectionRequest(
    val forge: SourceForge,
    val repositoryId: String,
    val requestedRef: String,
    val installationIdentity: String,
)

data class ForgeRepositoryEvidence(
    val forge: SourceForge,
    val immutableRepositoryId: String,
    val canonicalUrl: String,
    /** IDs and stable account names authenticated by the forge API. */
    val authenticatedInstallationIdentities: Set<String>,
)

data class ForgeTreeBlob(
    val path: String,
    val objectId: String,
)

data class ForgeLicenseEvidence(
    val path: String,
    val bytes: ByteArray,
    val spdxId: String? = null,
    val evidenceUrl: String? = null,
)

/** Immutable view returned by a forge integration for one resolved ref. */
data class ForgeSourceSnapshot(
    val repository: ForgeRepositoryEvidence,
    val requestedRef: String,
    val resolvedCommit: String,
    val treeObjectId: String? = null,
    val blobs: List<ForgeTreeBlob>,
    val licenses: List<ForgeLicenseEvidence>,
)

fun interface ForgeSourceClient {
    fun inspect(request: ForgeInspectionRequest): ForgeSourceSnapshot
}

enum class SourceVerificationFailure {
    UNSUPPORTED_FORGE,
    INVALID_INPUT,
    FORGE_TIMEOUT,
    FORGE_UNAVAILABLE,
    REPOSITORY_MISMATCH,
    INSTALLATION_MISMATCH,
    REQUESTED_REF_MISMATCH,
    MUTABLE_REF,
    TREE_INVALID,
    ARCHIVE_FILE_SET_MISMATCH,
    MANIFEST_MISSING,
    MANIFEST_AMBIGUOUS,
    PACKAGE_FILE_MISSING,
    PACKAGE_FILE_MISMATCH,
    LICENSE_MISSING,
    LICENSE_MISMATCH,
}

/** Stable public failure with a structured internal reason suitable for audit. */
class SourceVerificationException(
    val reason: SourceVerificationFailure,
    cause: Throwable? = null,
) : RuntimeException("Source proof verification failed", cause) {
    val code: String = "source_proof_invalid"
}

data class SourceVerificationResult(
    val forge: SourceForge,
    val repositoryId: String,
    val canonicalRepositoryUrl: String,
    val installationIdentity: String,
    val requestedRef: String,
    val resolvedCommit: String,
    /** Empty string means the package is at repository root. */
    val packageDirectory: String,
    val treeObjectId: String?,
    val treeEvidenceSha256: String,
    val archiveSha256: String,
    val archiveFileSetSha256: String,
    val licenseSpdx: String,
    val licensePath: String,
    val licenseEvidenceSha256: String,
    val verifiedAt: Instant,
    /** Digest binding all public proof fields above. */
    val proofSha256: String,
)

/**
 * Verifies immutable source identity without invoking a compiler, shell, or any
 * package-controlled code. Forge access runs behind a strict overall deadline.
 */
class SourceVerifier(
    private val client: ForgeSourceClient,
    private val clock: Clock = Clock.systemUTC(),
    private val verificationTimeout: Duration = Duration.ofSeconds(20),
    private val executor: ExecutorService = daemonVerifierExecutor(),
) : AutoCloseable {
    init {
        require(!verificationTimeout.isNegative && !verificationTimeout.isZero) {
            "verificationTimeout must be positive"
        }
    }

    fun verify(input: SourceVerificationInput): SourceVerificationResult {
        validateInput(input)
        val snapshot = inspectBounded(input)
        verifyRepository(input, snapshot)

        if (snapshot.requestedRef != input.requestedRef) {
            fail(SourceVerificationFailure.REQUESTED_REF_MISMATCH)
        }
        if (snapshot.resolvedCommit != input.expectedCommit) {
            fail(SourceVerificationFailure.MUTABLE_REF)
        }
        val objectAlgorithm = objectAlgorithm(snapshot.resolvedCommit)
        snapshot.treeObjectId?.let { requireObjectId(it, objectAlgorithm) }

        val tree = linkedMapOf<String, String>()
        snapshot.blobs.forEach { blob ->
            val path = requireRelativePath(blob.path)
            requireObjectId(blob.objectId, objectAlgorithm)
            if (tree.put(path, blob.objectId) != null) fail(SourceVerificationFailure.TREE_INVALID)
        }
        if (tree.isEmpty()) fail(SourceVerificationFailure.TREE_INVALID)

        val archive = linkedMapOf<String, ByteArray>()
        input.archiveFiles.forEach { file ->
            val path = requireRelativePath(file.path)
            if (archive.put(path, file.bytes.copyOf()) != null) {
                fail(SourceVerificationFailure.ARCHIVE_FILE_SET_MISMATCH)
            }
        }
        if (archive.keys != input.declaredPackagePaths || archive.keys.none { it == ROOT_MANIFEST }) {
            fail(SourceVerificationFailure.ARCHIVE_FILE_SET_MISMATCH)
        }
        if (!archive.getValue(ROOT_MANIFEST).contentEquals(input.manifestBytes)) {
            fail(SourceVerificationFailure.MANIFEST_MISSING)
        }

        val manifestObjectId = gitBlobObjectId(input.manifestBytes, objectAlgorithm)
        val manifestCandidates = tree.entries.filter { (path, objectId) ->
            (path == ROOT_MANIFEST || path.endsWith("/$ROOT_MANIFEST")) && objectId == manifestObjectId
        }
        if (manifestCandidates.isEmpty()) fail(SourceVerificationFailure.MANIFEST_MISSING)
        if (manifestCandidates.size != 1) fail(SourceVerificationFailure.MANIFEST_AMBIGUOUS)
        val packageDirectory = manifestCandidates.single().key.substringBeforeLast('/', "")

        archive.forEach { (relativePath, bytes) ->
            val repositoryPath = if (packageDirectory.isEmpty()) relativePath else "$packageDirectory/$relativePath"
            val treeObjectId = tree[repositoryPath]
                ?: fail(SourceVerificationFailure.PACKAGE_FILE_MISSING)
            if (treeObjectId != gitBlobObjectId(bytes, objectAlgorithm)) {
                fail(SourceVerificationFailure.PACKAGE_FILE_MISMATCH)
            }
        }

        val license = selectLicense(snapshot.licenses, packageDirectory, input.licenseSpdx)
        val verifiedAt = clock.instant()
        val treeEvidenceSha256 = digestTree(tree)
        val archiveFileSetSha256 = digestArchiveFiles(archive, objectAlgorithm)
        val licenseEvidenceSha256 = sha256(license.bytes)
        val canonicalUrl = canonicalRepositoryUrl(snapshot.repository.canonicalUrl)
        val proofSha256 = framedSha256(
            input.forge.wireName,
            input.repositoryId,
            canonicalUrl,
            input.installationIdentity,
            input.requestedRef,
            snapshot.resolvedCommit,
            packageDirectory,
            snapshot.treeObjectId.orEmpty(),
            treeEvidenceSha256,
            input.archiveSha256,
            archiveFileSetSha256,
            input.licenseSpdx,
            license.path,
            licenseEvidenceSha256,
            verifiedAt.toString(),
        )
        return SourceVerificationResult(
            forge = input.forge,
            repositoryId = input.repositoryId,
            canonicalRepositoryUrl = canonicalUrl,
            installationIdentity = input.installationIdentity,
            requestedRef = input.requestedRef,
            resolvedCommit = snapshot.resolvedCommit,
            packageDirectory = packageDirectory,
            treeObjectId = snapshot.treeObjectId,
            treeEvidenceSha256 = treeEvidenceSha256,
            archiveSha256 = input.archiveSha256,
            archiveFileSetSha256 = archiveFileSetSha256,
            licenseSpdx = input.licenseSpdx,
            licensePath = license.path,
            licenseEvidenceSha256 = licenseEvidenceSha256,
            verifiedAt = verifiedAt,
            proofSha256 = proofSha256,
        )
    }

    private fun inspectBounded(input: SourceVerificationInput): ForgeSourceSnapshot {
        val future = executor.submit<ForgeSourceSnapshot> {
            client.inspect(ForgeInspectionRequest(input.forge, input.repositoryId, input.requestedRef, input.installationIdentity))
        }
        return try {
            future.get(verificationTimeout.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            future.cancel(true)
            throw SourceVerificationException(SourceVerificationFailure.FORGE_TIMEOUT, error)
        } catch (error: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw SourceVerificationException(SourceVerificationFailure.FORGE_UNAVAILABLE, error)
        } catch (error: ExecutionException) {
            val cause = error.cause ?: error
            val reason = if (cause is HttpTimeoutException || cause.cause is HttpTimeoutException) {
                SourceVerificationFailure.FORGE_TIMEOUT
            } else {
                SourceVerificationFailure.FORGE_UNAVAILABLE
            }
            throw SourceVerificationException(reason, cause)
        }
    }

    private fun verifyRepository(input: SourceVerificationInput, snapshot: ForgeSourceSnapshot) {
        val observedCanonicalUrl = runCatching { canonicalRepositoryUrl(snapshot.repository.canonicalUrl) }
            .getOrElse { fail(SourceVerificationFailure.REPOSITORY_MISMATCH) }
        if (snapshot.repository.forge != input.forge ||
            snapshot.repository.immutableRepositoryId != input.repositoryId ||
            observedCanonicalUrl != canonicalRepositoryUrl(input.canonicalRepositoryUrl)
        ) {
            fail(SourceVerificationFailure.REPOSITORY_MISMATCH)
        }
        if (input.installationIdentity !in snapshot.repository.authenticatedInstallationIdentities) {
            fail(SourceVerificationFailure.INSTALLATION_MISMATCH)
        }
    }

    private fun selectLicense(
        candidates: List<ForgeLicenseEvidence>,
        packageDirectory: String,
        expectedSpdx: String,
    ): ForgeLicenseEvidence {
        val valid = candidates.filter { evidence ->
            evidence.bytes.isNotEmpty() && runCatching { requireRelativePath(evidence.path) }.isSuccess
        }
        val packageLicenses = valid.filter { evidence ->
            val parent = evidence.path.substringBeforeLast('/', "")
            parent == packageDirectory && isLicenseName(evidence.path.substringAfterLast('/'))
        }
        val repositoryLicenses = valid.filter { evidence ->
            '/' !in evidence.path && isLicenseName(evidence.path)
        }
        val selected = (packageLicenses.ifEmpty { repositoryLicenses })
            .sortedBy { it.path.lowercase(Locale.ROOT) }
            .firstOrNull()
            ?: fail(SourceVerificationFailure.LICENSE_MISSING)
        selected.spdxId?.takeUnless { it == "NOASSERTION" || it == "OTHER" }?.let { actual ->
            if (!actual.equals(expectedSpdx, ignoreCase = true)) fail(SourceVerificationFailure.LICENSE_MISMATCH)
        }
        return selected
    }

    private fun validateInput(input: SourceVerificationInput) {
        if (input.repositoryId.isBlank() || input.repositoryId.length > 128 ||
            input.installationIdentity.isBlank() || input.installationIdentity.length > 128 ||
            input.requestedRef.isBlank() || input.requestedRef.length > 255 ||
            input.licenseSpdx.isBlank() || input.licenseSpdx.length > 128 ||
            !SHA256.matches(input.archiveSha256) || !GIT_OBJECT_ID.matches(input.expectedCommit) ||
            input.manifestBytes.isEmpty() || input.archiveFiles.isEmpty()
        ) {
            fail(SourceVerificationFailure.INVALID_INPUT)
        }
        runCatching { canonicalRepositoryUrl(input.canonicalRepositoryUrl) }
            .getOrElse { fail(SourceVerificationFailure.INVALID_INPUT) }
        val declared = input.declaredPackagePaths.map(::requireRelativePath).toSet()
        if (declared != input.declaredPackagePaths || ROOT_MANIFEST !in declared) {
            fail(SourceVerificationFailure.INVALID_INPUT)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private companion object {
        const val ROOT_MANIFEST = "Seen.toml"
        val SHA256 = Regex("^[0-9a-f]{64}$")
        val GIT_OBJECT_ID = Regex("^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
    }
}

private enum class GitObjectAlgorithm(val jcaName: String, val hexLength: Int) {
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64),
}

private fun objectAlgorithm(commit: String): GitObjectAlgorithm = when (commit.length) {
    40 -> GitObjectAlgorithm.SHA1
    64 -> GitObjectAlgorithm.SHA256
    else -> fail(SourceVerificationFailure.TREE_INVALID)
}

private fun requireObjectId(value: String, algorithm: GitObjectAlgorithm) {
    if (value.length != algorithm.hexLength || value.any { it !in "0123456789abcdef" }) {
        fail(SourceVerificationFailure.TREE_INVALID)
    }
}

internal fun gitBlobObjectId(bytes: ByteArray, sha256ObjectFormat: Boolean = false): String =
    gitBlobObjectId(bytes, if (sha256ObjectFormat) GitObjectAlgorithm.SHA256 else GitObjectAlgorithm.SHA1)

private fun gitBlobObjectId(bytes: ByteArray, algorithm: GitObjectAlgorithm): String {
    val digest = MessageDigest.getInstance(algorithm.jcaName)
    digest.update("blob ${bytes.size}\u0000".toByteArray(StandardCharsets.UTF_8))
    digest.update(bytes)
    return digest.digest().toHexLower()
}

private fun requireRelativePath(value: String): String {
    if (value.isBlank() || value.startsWith('/') || '\\' in value || value.any { it.code < 0x20 }) {
        fail(SourceVerificationFailure.INVALID_INPUT)
    }
    val parts = value.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) fail(SourceVerificationFailure.INVALID_INPUT)
    return value
}

private fun canonicalRepositoryUrl(value: String): String {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    val path = uri.path.trimEnd('/').removeSuffix(".git")
    require(path.isNotBlank() && path != "/")
    return URI("https", null, uri.host.lowercase(Locale.ROOT), uri.port, path, null, null).toASCIIString()
}

private fun isLicenseName(value: String): Boolean {
    val upper = value.uppercase(Locale.ROOT)
    return upper == "LICENSE" || upper.startsWith("LICENSE.") || upper == "COPYING" ||
        upper.startsWith("COPYING.") || upper == "NOTICE" || upper.startsWith("NOTICE.")
}

private fun digestTree(tree: Map<String, String>): String = framedSha256(
    *tree.entries.sortedBy { it.key }.flatMap { listOf(it.key, it.value) }.toTypedArray(),
)

private fun digestArchiveFiles(files: Map<String, ByteArray>, algorithm: GitObjectAlgorithm): String = framedSha256(
    *files.entries.sortedBy { it.key }
        .flatMap { (path, bytes) -> listOf(path, gitBlobObjectId(bytes, algorithm)) }
        .toTypedArray(),
)

private fun framedSha256(vararg values: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(bytes.size.toLong()).array())
        digest.update(bytes)
    }
    return digest.digest().toHexLower()
}

private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

private fun fail(reason: SourceVerificationFailure): Nothing = throw SourceVerificationException(reason)

private fun daemonVerifierExecutor(): ExecutorService = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "seen-source-verifier").apply { isDaemon = true }
}

data class ForgeBearerCredential(
    val token: String,
    /** Identities cryptographically bound by the credential provider. */
    val authenticatedInstallationIdentities: Set<String> = emptySet(),
)

fun interface ForgeBearerTokenProvider {
    /** Returns a short-lived credential scoped to this installation and repository. */
    fun credential(
        forge: SourceForge,
        repositoryId: String,
        installationIdentity: String,
    ): ForgeBearerCredential
}

/**
 * Mints one repository-scoped GitHub App installation token per invocation.
 *
 * A downloaded GitHub App private key is long-lived secret material; the
 * installation token returned here is not. GitHub expires installation tokens
 * after one hour, so persisting one in deployment configuration would make the
 * mandatory second source check at +72 hours impossible.
 */
class GithubAppInstallationTokenProvider(
    appId: String,
    privateKeyPem: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
    private val githubApiBase: URI = URI("https://api.github.com"),
    private val clock: Clock = Clock.systemUTC(),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
    private val maximumResponseBytes: Int = 1024 * 1024,
) {
    private val appId = appId.trim().also {
        require(it.toLongOrNull()?.let { value -> value > 0 } == true) { "GitHub App ID must be a positive integer" }
    }
    private val privateKey: PrivateKey = parseGithubAppPrivateKey(privateKeyPem)

    init {
        require(!requestTimeout.isNegative && !requestTimeout.isZero)
        require(maximumResponseBytes in 1..8 * 1024 * 1024)
    }

    fun credential(repositoryId: String, installationIdentity: String): ForgeBearerCredential {
        val repository = repositoryId.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IOException("GitHub repository ID is invalid")
        val installation = installationIdentity.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IOException("GitHub installation ID is invalid")
        val jwt = appJwt()
        val installationRecord = requestJson(
            method = "GET",
            path = "/app/installations/$installation",
            bearerToken = jwt,
        ).jsonObject
        if (installationRecord.required("id") != installationIdentity || installationRecord.required("app_id") != appId) {
            throw IOException("GitHub App installation identity did not match")
        }

        val requestBody = buildJsonObject {
            put("repository_ids", buildJsonArray { add(JsonPrimitive(repository)) })
            put("permissions", buildJsonObject { put("contents", "read") })
        }.toString()
        val tokenRecord = requestJson(
            method = "POST",
            path = "/app/installations/$installation/access_tokens",
            bearerToken = jwt,
            requestBody = requestBody,
        ).jsonObject
        val token = tokenRecord.required("token")
        val expiresAt = runCatching { Instant.parse(tokenRecord.required("expires_at")) }
            .getOrElse { throw IOException("GitHub installation token expiry is invalid", it) }
        val now = clock.instant()
        if (token.isBlank() || token.any(Char::isWhitespace) ||
            !expiresAt.isAfter(now.plusSeconds(30)) || expiresAt.isAfter(now.plus(Duration.ofMinutes(65)))) {
            throw IOException("GitHub installation token response is invalid")
        }
        return ForgeBearerCredential(token, setOf(installationIdentity))
    }

    private fun appJwt(): String {
        val now = clock.instant()
        val header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".encodeToByteArray())
        val claims = base64Url(
            "{\"iat\":${now.minusSeconds(60).epochSecond},\"exp\":${now.plusSeconds(540).epochSecond},\"iss\":\"$appId\"}"
                .encodeToByteArray(),
        )
        val signingInput = "$header.$claims"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray(StandardCharsets.US_ASCII))
            sign()
        }
        return "$signingInput.${base64Url(signature)}"
    }

    private fun requestJson(
        method: String,
        path: String,
        bearerToken: String,
        requestBody: String? = null,
    ): JsonElement {
        val uri = URI(githubApiBase.toASCIIString().trimEnd('/') + "/" + path.trimStart('/'))
        val builder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .header("User-Agent", "seen-registry-source-verifier/1")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val request = when (method) {
            "GET" -> builder.GET().build()
            "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(requireNotNull(requestBody))).build()
            else -> error("Unsupported GitHub App request method")
        }
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() !in 200..299) throw IOException("GitHub App API returned HTTP ${response.statusCode()}")
            val bytes = readBounded(body, maximumResponseBytes)
            return try {
                RegistryJson.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8))
            } catch (error: Exception) {
                throw IOException("GitHub App API returned malformed JSON", error)
            }
        }
    }
}

/**
 * Production forge adapter using only the JDK HTTP client. It authenticates the
 * installation, resolves the requested ref once, and reads only immutable tree,
 * blob, and license evidence. Redirects and oversized provider responses fail.
 */
class JdkHttpForgeSourceClient(
    private val tokenProvider: ForgeBearerTokenProvider,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
    private val githubApiBase: URI = URI("https://api.github.com"),
    private val gitlabApiBase: URI = URI("https://gitlab.com/api/v4"),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
    private val maximumResponseBytes: Int = 8 * 1024 * 1024,
    private val maximumTreeEntries: Int = 100_000,
) : ForgeSourceClient {
    init {
        require(!requestTimeout.isNegative && !requestTimeout.isZero)
        require(maximumResponseBytes in 1..64 * 1024 * 1024)
        require(maximumTreeEntries in 1..1_000_000)
    }

    override fun inspect(request: ForgeInspectionRequest): ForgeSourceSnapshot {
        val credential = tokenProvider.credential(request.forge, request.repositoryId, request.installationIdentity)
        val token = credential.token
        require(token.isNotBlank() && token.none(Char::isWhitespace)) { "Forge token is unavailable" }
        return when (request.forge) {
            SourceForge.GITHUB -> inspectGithub(request, credential)
            SourceForge.GITLAB -> inspectGitlab(request, token)
        }
    }

    private fun inspectGithub(request: ForgeInspectionRequest, credential: ForgeBearerCredential): ForgeSourceSnapshot {
        val token = credential.token
        val repository = githubJson("/repositories/${pathSegment(request.repositoryId)}", token).jsonObject
        val repositoryId = repository.required("id")
        val canonicalUrl = repository.required("html_url")
        val fullName = repository.required("full_name")
        val repositoryPath = githubRepositoryPath(fullName)
        val commit = githubJson(
            "/repos/$repositoryPath/commits/${pathSegment(request.requestedRef)}",
            token,
        ).jsonObject
        val resolvedCommit = commit.required("sha")
        val treeObjectId = commit["commit"]!!.jsonObject["tree"]!!.jsonObject.required("sha")
        val treePayload = githubJson(
            "/repos/$repositoryPath/git/trees/${pathSegment(treeObjectId)}?recursive=1",
            token,
        ).jsonObject
        if (treePayload["truncated"]?.jsonPrimitive?.booleanOrNull == true) throw IOException("Forge tree was truncated")
        val blobs = treePayload["tree"]!!.jsonArray.mapNotNull { element ->
            val value = element.jsonObject
            if (value["type"]?.jsonPrimitive?.contentOrNull != "blob") null
            else ForgeTreeBlob(value.required("path"), value.required("sha"))
        }.also(::requireTreeBound)

        val license = githubJsonOrNull(
            "/repos/$repositoryPath/license?ref=${queryValue(resolvedCommit)}",
            token,
        )?.jsonObject
        val licenses = if (license != null) {
            val bytes = license["content"]?.jsonPrimitive?.contentOrNull?.decodeBase64()
            if (bytes == null) emptyList() else listOf(ForgeLicenseEvidence(
                path = license["path"]?.jsonPrimitive?.contentOrNull ?: "LICENSE",
                bytes = bytes,
                spdxId = license["license"]?.jsonObject?.get("spdx_id")?.jsonPrimitive?.contentOrNull,
                evidenceUrl = license["html_url"]?.jsonPrimitive?.contentOrNull,
            ))
        } else {
            githubTreeLicenses(repositoryPath, blobs, token)
        }
        val stableRepository = githubJson("/repositories/${pathSegment(request.repositoryId)}", token).jsonObject
        if (stableRepository.required("id") != repositoryId || stableRepository.required("full_name") != fullName ||
            stableRepository.required("html_url") != canonicalUrl) {
            throw IOException("GitHub repository identity changed during verification")
        }
        return ForgeSourceSnapshot(
            repository = ForgeRepositoryEvidence(
                SourceForge.GITHUB,
                repositoryId,
                canonicalUrl,
                credential.authenticatedInstallationIdentities,
            ),
            requestedRef = request.requestedRef,
            resolvedCommit = resolvedCommit,
            treeObjectId = treeObjectId,
            blobs = blobs,
            licenses = licenses,
        )
    }

    private fun githubTreeLicenses(repositoryPath: String, blobs: List<ForgeTreeBlob>, token: String): List<ForgeLicenseEvidence> =
        licenseCandidates(blobs).mapNotNull { candidate ->
            val payload = githubJsonOrNull(
                "/repos/$repositoryPath/git/blobs/${pathSegment(candidate.objectId)}",
                token,
            )?.jsonObject ?: return@mapNotNull null
            payload["content"]?.jsonPrimitive?.contentOrNull?.decodeBase64()?.let { bytes ->
                ForgeLicenseEvidence(candidate.path, bytes)
            }
        }

    private fun inspectGitlab(request: ForgeInspectionRequest, token: String): ForgeSourceSnapshot {
        val projectPath = "/projects/${pathSegment(request.repositoryId)}"
        val repository = gitlabJson(projectPath, token).jsonObject
        val repositoryId = repository.required("id")
        val canonicalUrl = repository.required("web_url")
        val user = gitlabJson("/user", token).jsonObject
        val identities = buildSet {
            add(user.required("id"))
            user["username"]?.jsonPrimitive?.contentOrNull?.let(::add)
        }
        val commit = gitlabJson(
            "$projectPath/repository/commits/${pathSegment(request.requestedRef)}",
            token,
        ).jsonObject
        val resolvedCommit = commit.required("id")
        val blobs = mutableListOf<ForgeTreeBlob>()
        var page = 1
        while (true) {
            val response = request(
                gitlabApiBase,
                "$projectPath/repository/tree?ref=${queryValue(resolvedCommit)}&recursive=true&per_page=100&page=$page",
                token,
                "application/json",
                "PRIVATE-TOKEN",
            ) ?: error("Unexpected missing tree response")
            parseJson(response.bytes).jsonArray.forEach { element ->
                val value = element.jsonObject
                if (value["type"]?.jsonPrimitive?.contentOrNull == "blob") {
                    blobs += ForgeTreeBlob(value.required("path"), value.required("id"))
                    requireTreeBound(blobs)
                }
            }
            val next = response.nextPage
            if (next == null) break
            page = next
        }
        val licenses = licenseCandidates(blobs).mapNotNull { candidate ->
            val response = request(
                gitlabApiBase,
                "$projectPath/repository/blobs/${pathSegment(candidate.objectId)}/raw",
                token,
                "application/octet-stream",
                "PRIVATE-TOKEN",
                allowNotFound = true,
            ) ?: return@mapNotNull null
            ForgeLicenseEvidence(
                path = candidate.path,
                bytes = response.bytes,
                evidenceUrl = "$canonicalUrl/-/blob/$resolvedCommit/${candidate.path}",
            )
        }
        return ForgeSourceSnapshot(
            repository = ForgeRepositoryEvidence(SourceForge.GITLAB, repositoryId, canonicalUrl, identities),
            requestedRef = request.requestedRef,
            resolvedCommit = resolvedCommit,
            treeObjectId = null,
            blobs = blobs,
            licenses = licenses,
        )
    }

    private fun githubJson(path: String, token: String): JsonElement =
        parseJson(requireNotNull(request(githubApiBase, path, token, "application/vnd.github+json", "Authorization")).bytes)

    private fun githubJsonOrNull(path: String, token: String): JsonElement? =
        request(githubApiBase, path, token, "application/vnd.github+json", "Authorization", allowNotFound = true)
            ?.let { parseJson(it.bytes) }

    private fun gitlabJson(path: String, token: String): JsonElement =
        parseJson(requireNotNull(request(gitlabApiBase, path, token, "application/json", "PRIVATE-TOKEN")).bytes)

    private fun request(
        base: URI,
        pathAndQuery: String,
        token: String,
        accept: String,
        authenticationHeader: String,
        allowNotFound: Boolean = false,
    ): HttpPayload? {
        val uri = URI(base.toASCIIString().trimEnd('/') + "/" + pathAndQuery.trimStart('/'))
        val authValue = if (authenticationHeader == "Authorization") "Bearer $token" else token
        val httpRequest = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Accept", accept)
            .header("User-Agent", "seen-registry-source-verifier/1")
            .apply {
                if (authenticationHeader == "Authorization") header("X-GitHub-Api-Version", "2022-11-28")
            }
            .header(authenticationHeader, authValue)
            .GET()
            .build()
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (allowNotFound && response.statusCode() == 404) return null
            if (response.statusCode() != 200) throw IOException("Forge returned HTTP ${response.statusCode()}")
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = body.read(buffer)
                if (read < 0) break
                if (output.size() + read > maximumResponseBytes) throw IOException("Forge response exceeded limit")
                output.write(buffer, 0, read)
            }
            val nextPage = response.headers().firstValue("X-Next-Page").orElse("")
                .takeIf(String::isNotBlank)?.toIntOrNull()
            return HttpPayload(output.toByteArray(), nextPage)
        }
    }

    private fun parseJson(bytes: ByteArray): JsonElement = try {
        RegistryJson.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8))
    } catch (error: Exception) {
        throw IOException("Forge returned malformed JSON", error)
    }

    private fun licenseCandidates(blobs: List<ForgeTreeBlob>): List<ForgeTreeBlob> = blobs
        .filter { isLicenseName(it.path.substringAfterLast('/')) }
        .sortedWith(compareBy<ForgeTreeBlob> { it.path.count { character -> character == '/' } }.thenBy { it.path })
        .take(MAX_LICENSE_CANDIDATES)

    private fun requireTreeBound(blobs: Collection<ForgeTreeBlob>) {
        if (blobs.size > maximumTreeEntries) throw IOException("Forge tree exceeded limit")
    }

    private data class HttpPayload(val bytes: ByteArray, val nextPage: Int?)

    private companion object {
        const val MAX_LICENSE_CANDIDATES = 16
    }
}

private fun JsonObject.required(name: String): String = this[name]?.jsonPrimitive?.contentOrNull
    ?: throw IOException("Forge response is missing $name")

private fun githubRepositoryPath(fullName: String): String {
    val parts = fullName.split('/')
    if (parts.size != 2 || parts.any(String::isBlank)) throw IOException("GitHub repository full name is invalid")
    return parts.joinToString("/") { pathSegment(it) }
}

private fun parseGithubAppPrivateKey(pem: String): PrivateKey {
    val trimmed = pem.trim()
    val type = when {
        trimmed.startsWith("-----BEGIN PRIVATE KEY-----") && trimmed.endsWith("-----END PRIVATE KEY-----") -> "pkcs8"
        trimmed.startsWith("-----BEGIN RSA PRIVATE KEY-----") && trimmed.endsWith("-----END RSA PRIVATE KEY-----") -> "pkcs1"
        else -> throw IllegalArgumentException("GitHub App private key must be unencrypted PKCS#8 or PKCS#1 PEM")
    }
    val encoded = trimmed.lineSequence()
        .filterNot { it.startsWith("-----") }
        .joinToString("")
    val der = runCatching { Base64.getDecoder().decode(encoded) }
        .getOrElse { throw IllegalArgumentException("GitHub App private key PEM is invalid", it) }
    val pkcs8 = if (type == "pkcs8") der else {
        val rsa = runCatching { RSAPrivateKey.getInstance(der) }
            .getOrElse { throw IllegalArgumentException("GitHub App RSA private key is invalid", it) }
        PrivateKeyInfo(
            AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
            rsa,
        ).encoded
    }
    return runCatching { KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8)) }
        .getOrElse { throw IllegalArgumentException("GitHub App private key is invalid", it) }
}

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

private fun readBounded(input: java.io.InputStream, maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (output.size() + read > maximumBytes) throw IOException("Forge response exceeded limit")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun pathSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
private fun queryValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
private fun String.decodeBase64(): ByteArray? = runCatching {
    Base64.getMimeDecoder().decode(this)
}.getOrNull()

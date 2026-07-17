package codes.yousef.seen.registry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TufSignerStateGuardTest {
    @Test
    fun `all six operations authorize only their exact roles against staged immutable metadata`() {
        val scenarios = listOf(
            releaseScenario(),
            securityScenario(),
            bootstrapScenario(),
            renewalScenario(),
            rotationScenario(TufRole.RELEASES),
            rotationScenario(TufRole.SECURITY),
        )

        scenarios.forEach { scenario ->
            scenario.roles.forEach { role ->
                scenario.guard(role).authorize(scenario.request(role, token = ALLOWED_ONE_TOKEN))
            }
            (TufRole.ONLINE.toSet() - scenario.roles).forEach { role ->
                assertFailsWith<TufSigningRequestException>("${scenario.operation.wireValue}:$role") {
                    scenario.guard(role).authorize(
                        scenario.requestForBytes(
                            role,
                            scenario.signedCandidate(scenario.roles.first()),
                            token = ALLOWED_ONE_TOKEN,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `caller bindings preserve multiple identities and reject wrong caller audience forged and expired tokens`() {
        val parsed = TufSignerStateGuardConfig.fromEnvironment(mapOf(
            "REGISTRY_TUF_SIGNER_METADATA_BUCKET" to "seen-metadata-dev",
            "REGISTRY_TUF_SIGNER_TRUSTED_ROOT_V1_SHA256" to "a".repeat(64),
            "REGISTRY_TUF_SIGNER_AUDIENCE" to AUDIENCE,
            "REGISTRY_TUF_SIGNER_CALLER_BINDINGS" to
                "release=$ALLOWED_ONE_EMAIL,release=$ALLOWED_TWO_EMAIL,security=security@example.test",
        ))
        assertEquals(setOf(ALLOWED_ONE_EMAIL, ALLOWED_TWO_EMAIL), parsed.callerEmails.getValue(TufSigningOperation.RELEASE))

        listOf(ALLOWED_ONE_TOKEN, ALLOWED_TWO_TOKEN).forEach { token ->
            val scenario = releaseScenario()
            scenario.guard(TufRole.RELEASES).authorize(scenario.request(TufRole.RELEASES, token))
        }
        listOf(WRONG_TOKEN, FORGED_TOKEN, EXPIRED_TOKEN).forEach { token ->
            val scenario = releaseScenario()
            assertFailsWith<TufSigningRequestException>(token) {
                scenario.guard(TufRole.RELEASES).authorize(scenario.request(TufRole.RELEASES, token))
            }
        }

        val wrongAudience = releaseScenario()
        assertFailsWith<TufSigningRequestException> {
            wrongAudience.guard(TufRole.RELEASES, audience = "https://wrong-audience.example.test")
                .authorize(wrongAudience.request(TufRole.RELEASES, ALLOWED_ONE_TOKEN))
        }
    }

    @Test
    fun `Google verifier binds exact issuer audience and verified email while failing closed on token verification`() {
        val calls = mutableListOf<Triple<String, String, String>>()
        val verifier = GoogleOidcTufSignerCallerTokenVerifier { token, audience, issuer ->
            calls += Triple(token, audience, issuer)
            when (token) {
                "valid" -> mapOf("email" to ALLOWED_ONE_EMAIL, "email_verified" to true)
                "unverified" -> mapOf("email" to ALLOWED_ONE_EMAIL, "email_verified" to false)
                else -> error("signature or expiry rejected")
            }
        }

        assertEquals(ALLOWED_ONE_EMAIL, verifier.verify("valid", AUDIENCE).email)
        assertEquals(Triple("valid", AUDIENCE, "https://accounts.google.com"), calls.single())
        assertFailsWith<TufSigningRequestException> { verifier.verify("unverified", AUDIENCE) }
        assertFailsWith<TufSigningRequestException> { verifier.verify("forged", AUDIENCE) }
        assertFailsWith<TufSigningRequestException> { verifier.verify("expired", AUDIENCE) }
    }

    @Test
    fun `trusted chain rejects wrong root pin invalid root signature and committed descriptor mismatch`() {
        releaseScenario().also { scenario ->
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.RELEASES, rootPin = "0".repeat(64))
                    .authorize(scenario.request(TufRole.RELEASES))
            }
        }

        releaseScenario().also { scenario ->
            val invalidRoot = corruptSignature(scenario.store.bytes("1.root.json"))
            scenario.store.put("1.root.json", invalidRoot)
            scenario.store.put("root.json", invalidRoot)
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.RELEASES, rootPin = sha256(invalidRoot))
                    .authorize(scenario.request(TufRole.RELEASES))
            }
        }

        releaseScenario().also { scenario ->
            scenario.store.put("1.security.json", scenario.store.bytes("1.security.json") + byteArrayOf('\n'.code.toByte()))
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.RELEASES).authorize(scenario.request(TufRole.RELEASES))
            }
        }
    }

    @Test
    fun `snapshot rejects candidate hash mismatch and a forged candidate signature even under a valid snapshot signature`() {
        releaseScenario().also { scenario ->
            scenario.store.put(
                "${scenario.candidateVersion}.releases.json",
                scenario.store.bytes("${scenario.candidateVersion}.releases.json") + byteArrayOf('\n'.code.toByte()),
            )
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(scenario.request(TufRole.SNAPSHOT))
            }
        }

        releaseScenario().also { scenario ->
            val roleName = "${scenario.candidateVersion}.releases.json"
            val forged = corruptSignature(scenario.store.bytes(roleName))
            scenario.store.put(roleName, forged)
            val forgedSnapshot = scenario.rewriteSnapshot { meta ->
                meta["releases.json"] = testFileMeta(scenario.candidateVersion, forged)
            }
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(TufRole.SNAPSHOT, signedBytes(forgedSnapshot)),
                )
            }
        }
    }

    @Test
    fun `oversized committed objects and oversized descriptors are rejected before validation or fetch`() {
        releaseScenario().also { scenario ->
            scenario.store.put(
                "1.root.json",
                ByteArray(MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES.toInt() + 1),
            )
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.RELEASES).authorize(scenario.request(TufRole.RELEASES))
            }
        }

        releaseScenario().also { scenario ->
            val original = signedObject(scenario.store.bytes("${scenario.candidateVersion}.snapshot.json"))
            val signed = original.toMutableMap()
            val meta = original.getValue("meta").jsonObject.toMutableMap()
            val releases = meta.getValue("releases.json").jsonObject.toMutableMap()
            releases["length"] = JsonPrimitive(MAXIMUM_TUF_SIGNER_METADATA_OBJECT_BYTES + 1)
            meta["releases.json"] = JsonObject(releases)
            signed["meta"] = JsonObject(meta)
            val oversizedDescriptor = testEnvelope(JsonObject(signed), listOf(scenario.base.online.snapshot))
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(TufRole.SNAPSHOT, signedBytes(oversizedDescriptor)),
                )
            }
        }
    }

    @Test
    fun `release cannot roll security back and security cannot roll releases back`() {
        releaseScenario(currentVersion = 2).also { scenario ->
            val rollback = scenario.rewriteSnapshot { meta ->
                meta["security.json"] = testFileMeta(1, scenario.store.bytes("1.security.json"))
            }
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(TufRole.SNAPSHOT, signedBytes(rollback)),
                )
            }
        }

        securityScenario(currentVersion = 2).also { scenario ->
            val rollback = scenario.rewriteSnapshot { meta ->
                meta["releases.json"] = testFileMeta(1, scenario.store.bytes("1.releases.json"))
            }
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(TufRole.SNAPSHOT, signedBytes(rollback)),
                )
            }
        }
    }

    @Test
    fun `forced release and security refreshes advance only their authorized delegated role`() {
        releaseScenario().also { scenario ->
            val meta = signedObject(scenario.store.bytes("2.snapshot.json")).getValue("meta").jsonObject
            assertEquals(2L, meta.getValue("releases.json").jsonObject.getValue("version").toString().toLong())
            assertEquals(1L, meta.getValue("security.json").jsonObject.getValue("version").toString().toLong())
            assertEquals(
                sha256(scenario.store.bytes("1.security.json")),
                meta.getValue("security.json").jsonObject.getValue("hashes").jsonObject.getValue("sha256").toString().trim('"'),
            )
        }
        securityScenario().also { scenario ->
            val meta = signedObject(scenario.store.bytes("2.snapshot.json")).getValue("meta").jsonObject
            assertEquals(1L, meta.getValue("releases.json").jsonObject.getValue("version").toString().toLong())
            assertEquals(2L, meta.getValue("security.json").jsonObject.getValue("version").toString().toLong())
            assertEquals(
                sha256(scenario.store.bytes("1.releases.json")),
                meta.getValue("releases.json").jsonObject.getValue("hashes").jsonObject.getValue("sha256").toString().trim('"'),
            )
        }
    }

    @Test
    fun `renewal and both rotations reject role changes outside their declared scope`() {
        renewalScenario().also { scenario ->
            val releaseBytes = testEnvelope(
                testCommon("targets", scenario.candidateVersion, Duration.ofDays(7), scenario.clock).toMutableMap().apply {
                    put("targets", JsonObject(emptyMap()))
                }.let(::JsonObject),
                listOf(scenario.base.online.releases),
            )
            scenario.store.put("${scenario.candidateVersion}.releases.json", releaseBytes)
            val changed = scenario.rewriteSnapshot { meta ->
                meta["releases.json"] = testFileMeta(scenario.candidateVersion, releaseBytes)
            }
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(TufRole.SNAPSHOT, signedBytes(changed)),
                )
            }
        }

        rotationScenario(TufRole.RELEASES).also { scenario ->
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(
                        TufRole.SNAPSHOT,
                        scenario.signedCandidate(TufRole.SNAPSHOT),
                        operation = TufSigningOperation.TARGETS_ROTATION_SECURITY,
                    ),
                )
            }
        }
        rotationScenario(TufRole.SECURITY).also { scenario ->
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.SNAPSHOT).authorize(
                    scenario.requestForBytes(
                        TufRole.SNAPSHOT,
                        scenario.signedCandidate(TufRole.SNAPSHOT),
                        operation = TufSigningOperation.TARGETS_ROTATION_RELEASES,
                    ),
                )
            }
        }
    }

    @Test
    fun `timestamp signer alone commits by generation CAS and fails closed on a precondition conflict`() {
        releaseScenario().also { scenario ->
            val request = scenario.request(TufRole.TIMESTAMP)
            val guard = scenario.guard(TufRole.TIMESTAMP)
            val signature = scenario.base.online.timestamp.sign(request.canonicalSignedBytes)

            guard.commitTimestamp(request, signature, scenario.base.online.timestamp.publicKey)

            assertEquals(1, scenario.store.commitCalls)
            assertContentEquals(scenario.candidateTimestamp, scenario.store.bytes("timestamp.json"))
        }

        releaseScenario().also { scenario ->
            scenario.store.rejectCommits = true
            val request = scenario.request(TufRole.TIMESTAMP)
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.TIMESTAMP).commitTimestamp(
                    request,
                    scenario.base.online.timestamp.sign(request.canonicalSignedBytes),
                    scenario.base.online.timestamp.publicKey,
                )
            }
            assertEquals(1, scenario.store.commitCalls)
        }

        releaseScenario().also { scenario ->
            val request = scenario.request(TufRole.TIMESTAMP)
            val wrong = testSigner(99)
            assertFailsWith<TufSigningRequestException> {
                scenario.guard(TufRole.TIMESTAMP).commitTimestamp(
                    request,
                    wrong.sign(request.canonicalSignedBytes),
                    wrong.publicKey,
                )
            }
            assertEquals(0, scenario.store.commitCalls)
        }
    }

    @Test
    fun `remote timestamp mode never asks the coordinator storage to CAS timestamp`() {
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val backing = InMemoryRegistryObjectStorage()
        val timestampAuthority = testSigner(40)
        val committingTimestamp = object : TufSigner {
            override val publicKey: ByteArray = timestampAuthority.publicKey
            override val commitsTimestampPointer: Boolean = true
            override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
                val signature = timestampAuthority.sign(canonicalSignedBytes)
                val envelope = testEnvelopeFromSignature(canonicalSignedBytes, publicKey, signature)
                check(backing.replaceMetadataIfUnchanged("timestamp.json", null, envelope))
                return signature
            }
        }
        val online = TufOnlineSigners(testSigner(10), testSigner(20), testSigner(30), committingTimestamp)
        bootstrapOnly(backing, online, clock)
        var coordinatorTimestampCasCalls = 0
        val coordinatorStorage = object : RegistryObjectStorage by backing {
            override fun replaceMetadataIfUnchanged(filename: String, expected: ByteArray?, bytes: ByteArray): Boolean {
                if (filename == "timestamp.json") coordinatorTimestampCasCalls += 1
                return backing.replaceMetadataIfUnchanged(filename, expected, bytes)
            }
        }

        val version = TufPublisher(
            fixedVersionRepository(1), coordinatorStorage, online, ENVIRONMENT, REPOSITORY_ID, ORIGIN, clock,
        ).ensureInitialTransaction()

        assertEquals(1, version)
        assertEquals(0, coordinatorTimestampCasCalls)
        assertTrue(backing.getMetadata("timestamp.json") != null)
    }

    @Test
    fun `publisher recovers only an exact valid timestamp when the signer commits then loses its response`() {
        fun publish(commitExactCandidate: Boolean): Result<Long> {
            val clock = Clock.fixed(NOW, ZoneOffset.UTC)
            val backing = InMemoryRegistryObjectStorage()
            val timestampAuthority = testSigner(40)
            val commitThenFail = object : TufSigner {
                override val publicKey: ByteArray = timestampAuthority.publicKey
                override val commitsTimestampPointer: Boolean = true
                override val timestampCommitRecoveryTimeout: Duration =
                    if (commitExactCandidate) Duration.ofSeconds(1) else Duration.ZERO
                override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
                    val committedSigned = if (commitExactCandidate) {
                        canonicalSignedBytes
                    } else {
                        val value = RegistryJson.parseToJsonElement(canonicalSignedBytes.decodeToString()).jsonObject.toMutableMap()
                        value["version"] = JsonPrimitive(999)
                        canonicalJson(JsonObject(value))
                    }
                    val signature = timestampAuthority.sign(committedSigned)
                    val committedEnvelope = testEnvelopeFromSignature(committedSigned, publicKey, signature)
                    if (commitExactCandidate) {
                        Thread {
                            Thread.sleep(150)
                            check(backing.replaceMetadataIfUnchanged("timestamp.json", null, committedEnvelope))
                        }.apply { isDaemon = true }.start()
                    } else {
                        check(backing.replaceMetadataIfUnchanged("timestamp.json", null, committedEnvelope))
                    }
                    throw RemoteTufSigningException("response lost after commit")
                }
            }
            val online = TufOnlineSigners(testSigner(10), testSigner(20), testSigner(30), commitThenFail)
            bootstrapOnly(backing, online, clock)
            return runCatching {
                TufPublisher(
                    fixedVersionRepository(1), backing, online, ENVIRONMENT, REPOSITORY_ID, ORIGIN, clock,
                ).ensureInitialTransaction()
            }
        }

        assertEquals(1L, publish(commitExactCandidate = true).getOrThrow())
        assertFailsWith<RemoteTufSigningException> {
            publish(commitExactCandidate = false).getOrThrow()
        }
    }

    @Test
    fun `expired top-level targets fail closed while expired online roles may be replaced`() {
        val bootstrap = bootstrapScenario()
        val afterTargetsExpiry = Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC)
        assertFailsWith<TufSigningRequestException> {
            bootstrap.guard(TufRole.RELEASES, clock = afterTargetsExpiry)
                .authorize(bootstrap.request(TufRole.RELEASES))
        }

        val recovery = releaseScenario()
        val recoveryClock = Clock.fixed(NOW.plus(Duration.ofDays(8)), ZoneOffset.UTC)
        val refreshedRelease = testEnvelope(
            testCommon("targets", recovery.candidateVersion, Duration.ofDays(7), recoveryClock).toMutableMap().apply {
                put("targets", JsonObject(emptyMap()))
            }.let(::JsonObject),
            listOf(recovery.base.online.releases),
        )
        recovery.store.put("${recovery.candidateVersion}.releases.json", refreshedRelease)
        val refreshedSnapshot = recovery.rewriteSnapshot(clock = recoveryClock) { meta ->
            meta["releases.json"] = testFileMeta(recovery.candidateVersion, refreshedRelease)
        }
        recovery.store.put("${recovery.candidateVersion}.snapshot.json", refreshedSnapshot)
        recovery.guard(TufRole.SNAPSHOT, clock = recoveryClock).authorize(
            recovery.requestForBytes(TufRole.SNAPSHOT, signedBytes(refreshedSnapshot)),
        )
    }

    @Test
    fun `legacy delegated custom is accepted only as committed signed state`() {
        val scenario = releaseScenario()
        val legacyReleaseSigned = signedObject(scenario.store.bytes("1.releases.json")).toMutableMap().apply {
            put("targets", buildJsonObject {
                put("packages/legacy/1.0.0/linux/x86_64", buildJsonObject {
                    put("length", 1)
                    put("hashes", buildJsonObject { put("sha256", "1".repeat(64)) })
                    put("custom", buildJsonObject { put("legacy_v3_attestation", "opaque") })
                })
            })
        }.let(::JsonObject)
        val legacyRelease = testEnvelope(legacyReleaseSigned, listOf(scenario.base.online.releases))
        scenario.store.put("1.releases.json", legacyRelease)

        val committedSnapshotSigned = signedObject(scenario.store.bytes("1.snapshot.json")).toMutableMap()
        val committedMeta = committedSnapshotSigned.getValue("meta").jsonObject.toMutableMap().apply {
            put("releases.json", testFileMeta(1, legacyRelease))
        }
        committedSnapshotSigned["meta"] = JsonObject(committedMeta)
        val committedSnapshot = testEnvelope(JsonObject(committedSnapshotSigned), listOf(scenario.base.online.snapshot))
        scenario.store.put("1.snapshot.json", committedSnapshot)

        val committedTimestampSigned = signedObject(scenario.store.bytes("timestamp.json")).toMutableMap()
        committedTimestampSigned["meta"] = buildJsonObject {
            put("snapshot.json", testFileMeta(1, committedSnapshot))
        }
        scenario.store.replaceTimestamp(testEnvelope(JsonObject(committedTimestampSigned), listOf(scenario.base.online.timestamp)))

        scenario.guard(TufRole.SNAPSHOT).authorize(scenario.request(TufRole.SNAPSHOT))

        val policy = TufSignerServerConfig(
            cloudRunService = "seen-tuf-releases-dev",
            environment = ENVIRONMENT,
            repositoryId = REPOSITORY_ID,
            role = TufRole.RELEASES,
            kmsKeyVersion = "projects/seen-dev/locations/us/keyRings/seen-registry-dev/" +
                "cryptoKeys/seen-registry-dev-releases/cryptoKeyVersions/1",
            publicKeyHex = scenario.base.online.releases.publicKey.toTestHex(),
        ).signingPolicy()
        assertFailsWith<TufSigningRequestException> {
            RoleLockedTufSigningService(policy, scenario.base.online.releases, scenario.clock)
                .sign(TufRole.RELEASES, canonicalJson(legacyReleaseSigned))
        }
    }
}

private data class GuardBase(
    val clock: Clock,
    val storage: InMemoryRegistryObjectStorage,
    val online: TufOnlineSigners,
    val targetSigners: List<TufSigner>,
    val currentVersion: Long,
)

private data class GuardScenario(
    val operation: TufSigningOperation,
    val roles: Set<String>,
    val base: GuardBase,
    val candidateOnline: TufOnlineSigners,
    val store: TestSignerMetadataStore,
    val candidateTimestamp: ByteArray,
    val candidateVersion: Long,
) {
    val clock: Clock get() = base.clock

    fun guard(
        role: String,
        audience: String = AUDIENCE,
        rootPin: String = sha256(store.bytes("1.root.json")),
        clock: Clock = this.clock,
    ): StateAwareTufSignerPolicyGuard = StateAwareTufSignerPolicyGuard(
        role = role,
        environment = ENVIRONMENT,
        repositoryId = REPOSITORY_ID,
        config = TufSignerStateGuardConfig(
            metadataBucket = "seen-metadata-dev",
            objectPrefix = "v1",
            trustedRootV1Sha256 = rootPin,
            audience = audience,
            callerEmails = TufSigningOperation.entries.associateWith { setOf(ALLOWED_ONE_EMAIL, ALLOWED_TWO_EMAIL) },
        ),
        metadata = store,
        tokenVerifier = strictTokenVerifier,
        clock = clock,
    )

    fun request(
        role: String,
        token: String = ALLOWED_ONE_TOKEN,
    ): TufSignerAuthorizationRequest = requestForBytes(role, signedCandidate(role), token = token)

    fun requestForBytes(
        role: String,
        bytes: ByteArray,
        token: String = ALLOWED_ONE_TOKEN,
        operation: TufSigningOperation = this.operation,
    ): TufSignerAuthorizationRequest = TufSignerAuthorizationRequest(role, operation, token, bytes)

    fun signedCandidate(role: String): ByteArray = when (role) {
        TufRole.TIMESTAMP -> signedBytes(candidateTimestamp)
        else -> signedBytes(store.bytes("$candidateVersion.$role.json"))
    }

    fun rewriteSnapshot(
        clock: Clock = this.clock,
        transform: (MutableMap<String, JsonElement>) -> Unit,
    ): ByteArray {
        val original = signedObject(store.bytes("$candidateVersion.snapshot.json"))
        val signed = original.toMutableMap()
        if (clock != this.clock) {
            signed["expires"] = JsonPrimitive(clock.instant().plus(Duration.ofDays(1)).toString())
        }
        val meta = original.getValue("meta").jsonObject.toMutableMap()
        transform(meta)
        signed["meta"] = JsonObject(meta)
        return testEnvelope(JsonObject(signed), listOf(base.online.snapshot))
    }
}

private class TestSignerMetadataStore(
    private val objects: MutableMap<String, ByteArray>,
    private var timestampGeneration: Long?,
) : TufSignerMetadataStore {
    var rejectCommits: Boolean = false
    var commitCalls: Int = 0

    override fun get(filename: String): TufSignerMetadataObject? = objects[filename]?.let {
        TufSignerMetadataObject(it.copyOf(), if (filename == "timestamp.json") requireNotNull(timestampGeneration) else 1L)
    }

    override fun commitTimestamp(expectedGeneration: Long?, bytes: ByteArray): Boolean {
        commitCalls += 1
        if (rejectCommits || expectedGeneration != timestampGeneration) return false
        objects["timestamp.json"] = bytes.copyOf()
        timestampGeneration = (timestampGeneration ?: 0L) + 1L
        return true
    }

    fun bytes(filename: String): ByteArray = requireNotNull(objects[filename]) { filename }.copyOf()
    fun put(filename: String, bytes: ByteArray) { objects[filename] = bytes.copyOf() }
    fun replaceTimestamp(bytes: ByteArray) {
        objects["timestamp.json"] = bytes.copyOf()
        if (timestampGeneration == null) timestampGeneration = 1L
    }
}

private fun releaseScenario(currentVersion: Int = 1): GuardScenario = onlineScenario(
    TufSigningOperation.RELEASE,
    currentVersion,
)

private fun securityScenario(currentVersion: Int = 1): GuardScenario = onlineScenario(
    TufSigningOperation.SECURITY,
    currentVersion,
)

private fun onlineScenario(operation: TufSigningOperation, currentVersion: Int): GuardScenario {
    val base = committedBase(currentVersion)
    val candidateStorage = copyStorage(base.storage, currentVersion + 1)
    val signers = when (operation) {
        TufSigningOperation.RELEASE -> TufOnlineSigners(
            base.online.releases,
            PublicKeyOnlyTufSigner(base.online.security.publicKey),
            base.online.snapshot,
            base.online.timestamp,
        )
        TufSigningOperation.SECURITY -> TufOnlineSigners(
            PublicKeyOnlyTufSigner(base.online.releases.publicKey),
            base.online.security,
            base.online.snapshot,
            base.online.timestamp,
        )
        else -> error("unsupported")
    }
    val candidateVersion = currentVersion + 1L
    val publisher = TufPublisher(
        fixedVersionRepository(candidateVersion), candidateStorage, signers,
        ENVIRONMENT, REPOSITORY_ID, ORIGIN, base.clock,
    )
    if (operation == TufSigningOperation.RELEASE) {
        publisher.forceRefreshReleases()
    } else {
        publisher.forceRefreshSecurity()
    }
    val roles = if (operation == TufSigningOperation.RELEASE) {
        setOf(TufRole.RELEASES, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
    } else {
        setOf(TufRole.SECURITY, TufRole.SNAPSHOT, TufRole.TIMESTAMP)
    }
    return scenarioFrom(base, candidateStorage, signers, operation, roles, candidateVersion)
}

private fun bootstrapScenario(): GuardScenario {
    val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    val storage = InMemoryRegistryObjectStorage()
    val online = testOnlineSigners()
    val targets = (4..5).map(::testSigner)
    bootstrapOnly(storage, online, clock, targets)
    val base = GuardBase(clock, storage, online, targets, 0)
    val candidateStorage = copyStorage(storage, 1)
    TufPublisher(
        fixedVersionRepository(1), candidateStorage, online, ENVIRONMENT, REPOSITORY_ID, ORIGIN, clock,
    ).ensureInitialTransaction()
    return scenarioFrom(
        base,
        candidateStorage,
        online,
        TufSigningOperation.BOOTSTRAP,
        TufRole.ONLINE.toSet(),
        1,
        includeCommittedTimestamp = false,
    )
}

private fun renewalScenario(): GuardScenario {
    val base = committedBase(1)
    val candidateStorage = copyStorage(base.storage, 2)
    val renewed = TufTargetsRenewalPolicy(base.online.publicKeys(), ENVIRONMENT, REPOSITORY_ID, base.clock).prepare(
        requireNotNull(base.storage.getMetadata("root.json")),
        requireNotNull(base.storage.getMetadata("1.targets.json")),
        base.targetSigners,
    )
    val signers = TufOnlineSigners(
        PublicKeyOnlyTufSigner(base.online.releases.publicKey),
        PublicKeyOnlyTufSigner(base.online.security.publicKey),
        base.online.snapshot,
        base.online.timestamp,
    )
    TufPublisher(
        fixedVersionRepository(2), candidateStorage, signers, ENVIRONMENT, REPOSITORY_ID, ORIGIN, base.clock,
    ).importTargetsRenewal(renewed.targets)
    return scenarioFrom(
        base, candidateStorage, signers, TufSigningOperation.TARGETS_RENEWAL,
        setOf(TufRole.SNAPSHOT, TufRole.TIMESTAMP), 2,
    )
}

private fun rotationScenario(role: String): GuardScenario {
    val base = committedBase(1)
    val replacement = if (role == TufRole.RELEASES) testSigner(110) else testSigner(120)
    val signers = if (role == TufRole.RELEASES) {
        TufOnlineSigners(
            replacement,
            PublicKeyOnlyTufSigner(base.online.security.publicKey),
            base.online.snapshot,
            base.online.timestamp,
        )
    } else {
        TufOnlineSigners(
            PublicKeyOnlyTufSigner(base.online.releases.publicKey),
            replacement,
            base.online.snapshot,
            base.online.timestamp,
        )
    }
    val rotated = TufTargetsRenewalPolicy(signers.publicKeys(), ENVIRONMENT, REPOSITORY_ID, base.clock).prepareRotation(
        requireNotNull(base.storage.getMetadata("root.json")),
        requireNotNull(base.storage.getMetadata("1.targets.json")),
        base.targetSigners,
    )
    val candidateStorage = copyStorage(base.storage, 2)
    TufPublisher(
        fixedVersionRepository(2), candidateStorage, signers, ENVIRONMENT, REPOSITORY_ID, ORIGIN, base.clock,
    ).importTargetsRotation(rotated.targets, role)
    val operation = if (role == TufRole.RELEASES) {
        TufSigningOperation.TARGETS_ROTATION_RELEASES
    } else {
        TufSigningOperation.TARGETS_ROTATION_SECURITY
    }
    return scenarioFrom(base, candidateStorage, signers, operation, setOf(role, TufRole.SNAPSHOT, TufRole.TIMESTAMP), 2)
}

private fun scenarioFrom(
    base: GuardBase,
    candidateStorage: InMemoryRegistryObjectStorage,
    candidateOnline: TufOnlineSigners,
    operation: TufSigningOperation,
    roles: Set<String>,
    candidateVersion: Long,
    includeCommittedTimestamp: Boolean = true,
): GuardScenario {
    val objects = metadataObjects(base.storage, candidateVersion.toInt())
    metadataObjects(candidateStorage, candidateVersion.toInt()).forEach { (name, bytes) ->
        if (name != "timestamp.json") objects[name] = bytes
    }
    if (!includeCommittedTimestamp) objects.remove("timestamp.json")
    val timestamp = requireNotNull(candidateStorage.getMetadata("timestamp.json"))
    return GuardScenario(
        operation,
        roles,
        base,
        candidateOnline,
        TestSignerMetadataStore(objects, if (includeCommittedTimestamp) 7L else null),
        timestamp,
        candidateVersion,
    )
}

private fun committedBase(currentVersion: Int): GuardBase {
    val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    val storage = InMemoryRegistryObjectStorage()
    val online = testOnlineSigners()
    val targets = (4..5).map(::testSigner)
    bootstrapOnly(storage, online, clock, targets)
    val repository = InMemoryRegistryRepository()
    val publisher = TufPublisher(repository, storage, online, ENVIRONMENT, REPOSITORY_ID, ORIGIN, clock)
    publisher.ensureInitialTransaction()
    repeat(currentVersion - 1) { publisher.publish(emptyList()) }
    return GuardBase(clock, storage, online, targets, currentVersion.toLong())
}

private fun bootstrapOnly(
    storage: InMemoryRegistryObjectStorage,
    online: TufOnlineSigners,
    clock: Clock,
    targetSigners: List<TufSigner> = (4..5).map(::testSigner),
) {
    val root = (1..3).map(::testSigner)
    TufBootstrapper(
        storage,
        root.map(TufSigner::publicKey),
        root,
        targetSigners.map(TufSigner::publicKey),
        targetSigners,
        online.publicKeys(),
        ENVIRONMENT,
        REPOSITORY_ID,
        clock,
    ).bootstrap()
}

private fun copyStorage(source: InMemoryRegistryObjectStorage, maximumVersion: Int): InMemoryRegistryObjectStorage =
    InMemoryRegistryObjectStorage().also { target ->
        metadataObjects(source, maximumVersion).forEach(target::putMetadata)
    }

private fun metadataObjects(
    storage: RegistryObjectStorage,
    maximumVersion: Int,
): MutableMap<String, ByteArray> {
    val names = mutableSetOf("root.json", "timestamp.json")
    (1..maximumVersion).forEach { version ->
        listOf("root", "targets", TufRole.RELEASES, TufRole.SECURITY, TufRole.SNAPSHOT).forEach { role ->
            names += "$version.$role.json"
        }
    }
    return names.mapNotNull { name -> storage.getMetadata(name)?.let { name to it } }.toMap().toMutableMap()
}

private fun fixedVersionRepository(version: Long): RegistryRepository {
    val delegate = InMemoryRegistryRepository()
    return object : RegistryRepository by delegate {
        override fun nextMetadataVersion(): Long = version
    }
}

private val strictTokenVerifier = TufSignerCallerTokenVerifier { token, audience ->
    if (audience != AUDIENCE) throw TufSigningRequestException("wrong audience")
    when (token) {
        ALLOWED_ONE_TOKEN -> VerifiedTufSignerCaller(ALLOWED_ONE_EMAIL)
        ALLOWED_TWO_TOKEN -> VerifiedTufSignerCaller(ALLOWED_TWO_EMAIL)
        WRONG_TOKEN -> VerifiedTufSignerCaller("wrong@example.test")
        FORGED_TOKEN -> throw TufSigningRequestException("forged token")
        EXPIRED_TOKEN -> throw TufSigningRequestException("expired token")
        else -> throw TufSigningRequestException("unknown token")
    }
}

private fun signedObject(envelope: ByteArray): JsonObject =
    RegistryJson.parseToJsonElement(envelope.decodeToString()).jsonObject.getValue("signed").jsonObject

private fun signedBytes(envelope: ByteArray): ByteArray = canonicalJson(signedObject(envelope))

private fun testEnvelope(signed: JsonObject, signers: List<TufSigner>): ByteArray {
    val bytes = canonicalJson(signed)
    return canonicalJson(buildJsonObject {
        put("signatures", buildJsonArray {
            signers.sortedBy { tufKeyId(it.publicKey) }.forEach { signer ->
                add(buildJsonObject {
                    put("keyid", tufKeyId(signer.publicKey))
                    put("sig", signer.sign(bytes).toTestHex())
                })
            }
        })
        put("signed", signed)
    })
}

private fun testEnvelopeFromSignature(signedBytes: ByteArray, publicKey: ByteArray, signature: ByteArray): ByteArray =
    canonicalJson(buildJsonObject {
        put("signatures", buildJsonArray {
            add(buildJsonObject {
                put("keyid", tufKeyId(publicKey))
                put("sig", signature.toTestHex())
            })
        })
        put("signed", RegistryJson.parseToJsonElement(signedBytes.decodeToString()))
    })

private fun corruptSignature(envelopeBytes: ByteArray): ByteArray {
    val envelope = RegistryJson.parseToJsonElement(envelopeBytes.decodeToString()).jsonObject
    val signatures = envelope.getValue("signatures").jsonArray.map { signature ->
        val value = signature.jsonObject.toMutableMap()
        value["sig"] = JsonPrimitive("00".repeat(64))
        JsonObject(value)
    }
    return canonicalJson(buildJsonObject {
        put("signatures", JsonArray(signatures))
        put("signed", envelope.getValue("signed"))
    })
}

private fun testFileMeta(version: Long, bytes: ByteArray): JsonObject = buildJsonObject {
    put("version", version)
    put("length", bytes.size)
    put("hashes", buildJsonObject { put("sha256", sha256(bytes)) })
}

private fun testCommon(type: String, version: Long, lifetime: Duration, clock: Clock): Map<String, JsonElement> = linkedMapOf(
    "_type" to JsonPrimitive(type),
    "spec_version" to JsonPrimitive("1.0"),
    "version" to JsonPrimitive(version),
    "expires" to JsonPrimitive(clock.instant().plus(lifetime).toString()),
    "environment" to JsonPrimitive(ENVIRONMENT),
    "repository_id" to JsonPrimitive(REPOSITORY_ID),
)

private fun ByteArray.toTestHex(): String = joinToString("") { "%02x".format(it) }

private const val ENVIRONMENT = "development"
private const val REPOSITORY_ID = "seen-dev-registry-v1"
private const val ORIGIN = "https://seen.dev.yousef.codes/packages"
private const val AUDIENCE = "https://seen-tuf-signer-dev.example.run.app"
private const val ALLOWED_ONE_EMAIL = "promoter@example.test"
private const val ALLOWED_TWO_EMAIL = "refresh@example.test"
private const val ALLOWED_ONE_TOKEN = "allowed-one"
private const val ALLOWED_TWO_TOKEN = "allowed-two"
private const val WRONG_TOKEN = "wrong"
private const val FORGED_TOKEN = "forged"
private const val EXPIRED_TOKEN = "expired"
private val NOW: Instant = Instant.parse("2026-07-16T00:00:00Z")

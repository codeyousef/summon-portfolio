package codes.yousef.seen.registry

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TufEnforcementMetadataPublisherTest {
    @Test
    fun `resolver metadata excludes legacy releases and emits reviewed proof digest`() {
        val fixture = fixture()
        val available = fixture.addReviewed(1, "available")
        val yanked = fixture.addReviewed(2, "yanked")
        assertTrue(fixture.repository.reserveRelease(legacyRelease()))

        assertEquals(2L, fixture.tuf.ensureFreshTransaction())

        val targets = currentTargets(fixture.storage, TufRole.RELEASES)
        assertEquals(setOf(targetPath(available.release), targetPath(yanked.release)), targets.keys)
        assertFalse(targets.keys.any { "/legacy/" in it })
        assertEquals("available", availability(targets.getValue(targetPath(available.release))))
        assertEquals("yanked", availability(targets.getValue(targetPath(yanked.release))))
        val custom = targets.getValue(targetPath(available.release)).jsonObject["custom"]!!.jsonObject
        assertEquals(available.proof.sha256(), custom["source_proof_sha256"]!!.jsonPrimitive.content)
        assertEquals(available.proof.sha256(), custom["provenance_sha256"]!!.jsonPrimitive.content)
        assertNotEquals(
            sha256(RegistryJson.encodeToString(available.release.source).encodeToByteArray()),
            custom["source_proof_sha256"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `security override is exact durable and reinstatement removes only its target`() {
        val fixture = fixture()
        val first = fixture.addReviewed(3, "available")
        val second = fixture.addReviewed(4, "yanked")
        fixture.tuf.publish(emptyList())
        val baseTargets = currentTargets(fixture.storage, TufRole.RELEASES)
        val metadata = TufEnforcementMetadataPublisher(fixture.repository, fixture.tuf)
        val enforcement = EnforcementService(fixture.repository, "development", fixture.clock)
        val security = EnforcementPrincipal("prn_security_00000001", setOf(EnforcementRoles.SECURITY))

        val firstRequest = SecurityQuarantineRequest("Immediate investigation", "critical")
        val firstAction = enforcement.securityQuarantineRelease(
            first.release.record.`package`,
            first.release.record.version,
            firstRequest,
            security,
        ) { metadata.publishSecurityQuarantine(first.subject, firstRequest) }
        assertTruthfulReference(fixture.storage, firstAction.signedMetadata)
        var securityTargets = currentTargets(fixture.storage, TufRole.SECURITY)
        assertEquals(setOf(targetPath(first.release)), securityTargets.keys)
        assertExactOverride(
            baseTargets.getValue(targetPath(first.release)),
            securityTargets.getValue(targetPath(first.release)),
        )

        val secondRequest = SecurityQuarantineRequest("Independent investigation", "high")
        val secondAction = enforcement.securityQuarantineRelease(
            second.release.record.`package`,
            second.release.record.version,
            secondRequest,
            security,
        ) { metadata.publishSecurityQuarantine(second.subject, secondRequest) }
        assertTruthfulReference(fixture.storage, secondAction.signedMetadata)
        securityTargets = currentTargets(fixture.storage, TufRole.SECURITY)
        assertEquals(setOf(targetPath(first.release), targetPath(second.release)), securityTargets.keys)

        val promoterSigners = TufOnlineSigners(
            fixture.online.releases,
            PublicKeyOnlyTufSigner(fixture.online.security.publicKey),
            fixture.online.snapshot,
            fixture.online.timestamp,
        )
        val promoter = TufPublisher(
            fixture.repository,
            fixture.storage,
            promoterSigners,
            "development",
            REPOSITORY_ID,
            ORIGIN,
            fixture.clock,
        )
        val promotionVersion = promoter.publish(emptyList())
        assertNull(fixture.storage.getMetadata("$promotionVersion.security.json"))
        assertEquals(
            secondAction.signedMetadata.version,
            currentRoleVersion(fixture.storage, TufRole.SECURITY),
        )
        assertEquals(2, currentTargets(fixture.storage, TufRole.SECURITY).size)
        val unauthorized = assertFailsWith<RegistryException> {
            promoter.publishSecurityQuarantine(fixture.repository.getRelease(
                first.release.record.`package`,
                first.release.record.version,
            )!!)
        }
        assertEquals("temporarily_unavailable", unauthorized.code)

        val reinstatement = metadata.publishReviewedReinstatement(
            first.subject,
            ReviewedReinstatementRequest(
                incidentId = firstAction.incidentId,
                appealId = "apl_0000000000000001",
                reviewId = "rev_0000000000000001",
            ),
        )
        assertTruthfulReference(fixture.storage, reinstatement)
        securityTargets = currentTargets(fixture.storage, TufRole.SECURITY)
        assertFalse(targetPath(first.release) in securityTargets)
        assertTrue(targetPath(second.release) in securityTargets)
        assertExactOverride(
            baseTargets.getValue(targetPath(second.release)),
            securityTargets.getValue(targetPath(second.release)),
        )

        // The durable release is still quarantined here. A metadata read must
        // reject the missing override and publish the state-derived deny role.
        val repairedVersion = fixture.tuf.ensureFreshTransaction()
        assertTrue(repairedVersion > reinstatement.version)
        securityTargets = currentTargets(fixture.storage, TufRole.SECURITY)
        assertEquals(setOf(targetPath(first.release), targetPath(second.release)), securityTargets.keys)
        assertExactOverride(
            baseTargets.getValue(targetPath(first.release)),
            securityTargets.getValue(targetPath(first.release)),
        )

        // The inverse mismatch is reconciled too: once durable availability is
        // restored, a stale deny override is removed from the next transaction.
        val quarantined = fixture.repository.getRelease(
            first.release.record.`package`,
            first.release.record.version,
        )!!
        val restored = quarantined.copy(
            record = quarantined.record.copy(
                state = quarantined.record.state.copy(availability = "available"),
            ),
            revision = quarantined.revision + 1,
        )
        assertTrue(fixture.repository.transitionRelease(quarantined.revision, restored) is ReleaseTransitionResult.Applied)
        val reconciledVersion = fixture.tuf.ensureFreshTransaction()
        assertTrue(reconciledVersion > repairedVersion)
        securityTargets = currentTargets(fixture.storage, TufRole.SECURITY)
        assertFalse(targetPath(first.release) in securityTargets)
        assertTrue(targetPath(second.release) in securityTargets)
    }

    @Test
    fun `committed state defeats stale snapshots while exact activation draft is admitted`() {
        val fixture = fixture()
        val committed = fixture.addReviewed(5, "yanked")
        fixture.tuf.publish(emptyList())

        val staleAvailable = committed.release.copy(
            record = committed.release.record.copy(
                state = committed.release.record.state.copy(availability = "available"),
            ),
        )
        fixture.tuf.publish(listOf(staleAvailable))
        assertEquals(
            "yanked",
            availability(currentTargets(fixture.storage, TufRole.RELEASES).getValue(targetPath(committed.release))),
        )

        val ready = fixture.addReviewed(6, "unavailable", lifecycle = "ready")
        val stateMachine = ReviewStateMachine(Duration.ofHours(72))
        val claimed = stateMachine.claimPromotion(
            ready.release,
            "act_0000000000000001",
            fixture.clock.instant(),
        )
        val applied = fixture.repository.transitionRelease(ready.release.revision, claimed)
        assertTrue(applied is ReleaseTransitionResult.Applied)
        val activationDraft = stateMachine.activate(
            claimed,
            "act_0000000000000001",
            metadataVersion = 99,
            fixture.clock.instant(),
        )

        fixture.tuf.publish(listOf(activationDraft))
        val targets = currentTargets(fixture.storage, TufRole.RELEASES)
        assertTrue(targetPath(activationDraft) in targets)
        assertEquals("available", availability(targets.getValue(targetPath(activationDraft))))
    }

    private fun fixture(): Fixture {
        val clock = MutableClock(Instant.parse("2026-07-17T00:00:00Z"))
        val repository = InMemoryRegistryRepository()
        val storage = InMemoryRegistryObjectStorage()
        val online = testOnlineSigners()
        val root = (1..3).map(::testSigner)
        val targets = (4..5).map(::testSigner)
        TufBootstrapper(
            storage,
            root.map(TufSigner::publicKey),
            root,
            targets.map(TufSigner::publicKey),
            targets,
            online.publicKeys(),
            "development",
            REPOSITORY_ID,
            clock,
        ).bootstrap()
        val tuf = TufPublisher(repository, storage, online, "development", REPOSITORY_ID, ORIGIN, clock)
        tuf.ensureInitialTransaction()
        return Fixture(clock, repository, storage, online, tuf)
    }

    private fun legacyRelease(): StoredRelease = StoredRelease(
        record = ReleaseRecord(
            `package` = "seen/legacy",
            version = "0.1.0",
            archive = ArchiveStats(sha256 = "f".repeat(64), compressedBytes = 64),
            manifestSha256 = "e".repeat(64),
            state = ReleaseState("active", "public", "available", "retained"),
            timestamps = ReleaseTimestamps(NOW, activatedAt = NOW, updatedAt = NOW),
            links = ReleaseLinks("/legacy", "/legacy/package", download = "/legacy/download"),
        ),
        ownerPrincipal = "publisher",
        uploadId = "upl_legacy_00000001",
        uploadExpiresAt = "2026-07-18T00:00:00Z",
        manifest = buildJsonObject { },
        source = source("legacy", "f"),
    )

    private fun assertExactOverride(base: JsonElement, override: JsonElement) {
        val baseObject = base.jsonObject
        val expectedCustom = baseObject["custom"]!!.jsonObject.toMutableMap().apply {
            put("availability", JsonPrimitive("security-quarantined"))
        }
        val expected = JsonObject(baseObject.toMutableMap().apply {
            put("custom", JsonObject(expectedCustom))
        })
        assertEquals(expected, override)
        assertEquals(baseObject["length"], override.jsonObject["length"])
        assertEquals(baseObject["hashes"], override.jsonObject["hashes"])
    }

    private fun assertTruthfulReference(storage: InMemoryRegistryObjectStorage, reference: SignedMetadataReference) {
        val bytes = assertNotNull(storage.getMetadata(reference.filename))
        assertEquals(bytes.size.toLong(), reference.length)
        assertEquals(sha256(bytes), reference.sha256)
        val signed = signed(bytes)
        assertEquals(reference.version, signed["version"]!!.jsonPrimitive.content.toLong())
        assertEquals(reference.expiresAt, signed["expires"]!!.jsonPrimitive.content)
        assertEquals(reference.version, currentRoleVersion(storage, TufRole.SECURITY))
        val timestamp = signed(storage.getMetadata("timestamp.json")!!)
        val snapshotMeta = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject
        assertEquals(sha256(storage.getMetadata("${snapshotMeta["version"]!!.jsonPrimitive.content}.snapshot.json")!!),
            snapshotMeta["hashes"]!!.jsonObject["sha256"]!!.jsonPrimitive.content)
    }

    private fun currentTargets(storage: InMemoryRegistryObjectStorage, role: String): JsonObject {
        val timestamp = signed(storage.getMetadata("timestamp.json")!!)
        val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!
            .jsonPrimitive.content.toLong()
        val snapshot = signed(storage.getMetadata("$snapshotVersion.snapshot.json")!!)
        val roleVersion = snapshot["meta"]!!.jsonObject["$role.json"]!!.jsonObject["version"]!!
            .jsonPrimitive.content.toLong()
        return signed(storage.getMetadata("$roleVersion.$role.json")!!)["targets"]!!.jsonObject
    }

    private fun currentRoleVersion(storage: InMemoryRegistryObjectStorage, role: String): Long {
        val timestamp = signed(storage.getMetadata("timestamp.json")!!)
        val snapshotVersion = timestamp["meta"]!!.jsonObject["snapshot.json"]!!.jsonObject["version"]!!
            .jsonPrimitive.content.toLong()
        val snapshot = signed(storage.getMetadata("$snapshotVersion.snapshot.json")!!)
        return snapshot["meta"]!!.jsonObject["$role.json"]!!.jsonObject["version"]!!.jsonPrimitive.content.toLong()
    }

    private fun signed(bytes: ByteArray): JsonObject =
        RegistryJson.parseToJsonElement(bytes.decodeToString()).jsonObject["signed"]!!.jsonObject

    private fun availability(target: JsonElement): String =
        target.jsonObject["custom"]!!.jsonObject["availability"]!!.jsonPrimitive.content

    private fun targetPath(stored: StoredRelease): String {
        val name = stored.record.`package`.substringAfter('/')
        return "packages/${stored.record.`package`}/${stored.record.version}/${stored.record.archive.sha256}/" +
            "$name-${stored.record.version}.seenpkg.tgz"
    }

    private fun source(name: String, digestCharacter: String) = SourceDeclaration(
        "github",
        "repository-$name",
        "installation-$name",
        "refs/tags/v1.2.3",
        digestCharacter.repeat(40),
        "MIT",
    )

    private data class Fixture(
        val clock: MutableClock,
        val repository: InMemoryRegistryRepository,
        val storage: InMemoryRegistryObjectStorage,
        val online: TufOnlineSigners,
        val tuf: TufPublisher,
    )

    private fun Fixture.addReviewed(
        seed: Int,
        availability: String,
        lifecycle: String = "active",
    ): ReviewedRelease {
        val value = reviewedRelease(seed, availability, lifecycle)
        assertTrue(repository.createPackage(StoredPackage(
            record = PackageRecord(
                identity = value.release.record.`package`,
                repository = value.proof.repository.canonicalUrl,
                licenseSpdx = "MIT",
                createdAt = NOW,
                updatedAt = NOW,
                links = PackageLinks("/package", "/releases"),
            ),
            ownerPrincipal = value.release.ownerPrincipal,
        )))
        assertTrue(repository.reserveRelease(value.release))
        assertTrue(repository.appendReviewArtifact(value.firstProof.toArtifact()))
        assertTrue(repository.appendReviewArtifact(value.firstScan.toArtifact()))
        assertTrue(repository.appendReviewArtifact(value.proof.toArtifact()))
        assertTrue(repository.appendReviewArtifact(value.scan.toArtifact()))
        return value
    }

    private data class ReviewedRelease(
        val release: StoredRelease,
        val firstProof: SourceProofRecord,
        val firstScan: ScanAttestationRecord,
        val proof: SourceProofRecord,
        val scan: ScanAttestationRecord,
    ) {
        val subject = EnforcementReleaseSubject(release.record.`package`, release.record.version)
    }

    private fun reviewedRelease(seed: Int, availability: String, lifecycle: String): ReviewedRelease {
        val suffix = seed.toString().padStart(16, '0')
        val packageIdentity = "seen/reviewed-$seed"
        val archive = "0123456789abcdef"[seed].toString().repeat(64)
        val firstProofId = "prf_${(seed * 10 + 1).toString().padStart(16, '0')}"
        val firstScanId = "scn_${(seed * 10 + 1).toString().padStart(16, '0')}"
        val proofId = "prf_${(seed * 10 + 2).toString().padStart(16, '0')}"
        val scanId = "scn_${(seed * 10 + 2).toString().padStart(16, '0')}"
        fun proof(id: String, sequence: Long, previous: String?) = SourceProofRecord(
            proofId = id,
            sequence = sequence,
            previousProofId = previous,
            packageIdentity = packageIdentity,
            version = VERSION,
            repository = SourceProofRepository(
                "github",
                "repository-$seed",
                "https://github.com/seen/reviewed-$seed",
                "installation-$seed",
            ),
            requestedRef = "refs/tags/v$VERSION",
            resolvedRef = "refs/tags/v$VERSION",
            commit = SourceProofCommit("sha1", "a".repeat(40)),
            archive = SourceProofArchive("b".repeat(64), archive),
            license = SourceProofLicense("MIT", "c".repeat(64), true),
            status = "verified",
            checks = listOf(
                "repository-identity",
                "installation-identity",
                "commit-resolution",
                "archive-digest",
                "license",
            ).map { SourceProofCheck(it, "passed", NOW, "d".repeat(64)) },
            verifiedAt = NOW,
            verifier = SourceProofVerifier("source-verifier", "source-proof-v1"),
        )
        fun scan(
            id: String,
            sequence: Long,
            previous: String?,
            phase: String,
            sourceProof: SourceProofRecord,
        ): ScanAttestationRecord {
            val proofSha = sourceProof.sha256()
            return ScanAttestationRecord(
                attestationId = id,
                sequence = sequence,
                previousAttestationId = previous,
                subject = ScanSubject(packageIdentity, VERSION, archive, sourceProof.proofId, proofSha),
                scan = ScanDescriptor(phase, 1, "package-scan-v1", "e".repeat(64)),
                scanner = ScannerIdentity("seen-package-scanner", "1.0.0", true, "none", "none", "read-only"),
                input = ScanInputBinding(archive, proofSha, true),
                sandbox = ScanSandbox(true, true, "none", 2_000, 536_870_912, 64),
                invocation = ScanInvocation("scan-run-$phase-$suffix", NOW, NOW, 300),
                result = ScanResult(
                    status = "passed",
                    disposition = "promotion-eligible",
                    observedArchiveSha256 = archive,
                    observedSourceProofSha256 = proofSha,
                    findings = emptyList(),
                    reportSha256 = "f".repeat(64),
                    evidenceSha256 = "a".repeat(64),
                ),
                generatedAt = NOW,
            )
        }
        val firstProof = proof(firstProofId, 1, null)
        val firstScan = scan(firstScanId, 2, null, "first", firstProof)
        val proof = proof(proofId, 3, firstProofId)
        val scan = scan(scanId, 4, firstScanId, "second", proof)
        val active = lifecycle == "active"
        val record = ReleaseRecord(
            `package` = packageIdentity,
            version = VERSION,
            archive = ArchiveStats(sha256 = archive, compressedBytes = 128),
            manifestSha256 = "9".repeat(64),
            state = ReleaseState(lifecycle, "public", availability, "retained"),
            sourceProofId = proofId,
            verification = ReleaseVerification("passed", "passed", "passed", "passed", "passed", 4),
            timestamps = ReleaseTimestamps(
                reservedAt = "2026-07-13T00:00:00Z",
                publicDelayStartedAt = "2026-07-14T00:00:00Z",
                publicDelayEndsAt = NOW,
                readyAt = NOW,
                activatedAt = NOW.takeIf { active },
                yankedAt = NOW.takeIf { availability == "yanked" },
                updatedAt = NOW,
            ),
            resolverMetadataVersion = 1L.takeIf { active },
            links = ReleaseLinks(
                "/packages/$packageIdentity/releases/$VERSION",
                "/packages/$packageIdentity",
                sourceProof = "/proof/$proofId",
                download = "/download/$archive".takeIf { active },
            ),
        )
        val release = StoredRelease(
            record = record,
            ownerPrincipal = "publisher-$seed",
            uploadId = "upl_$suffix",
            uploadExpiresAt = "2026-07-18T00:00:00Z",
            manifest = buildJsonObject { },
            source = source(seed.toString(), "a"),
            review = ReviewEvidenceState(
                validatedArchiveSha256 = archive,
                firstSourceProofId = firstProofId,
                firstSourceProofSha256 = firstProof.sha256(),
                firstScanAttestationId = firstScanId,
                firstScanAttestationSha256 = firstScan.sha256(),
                secondSourceProofId = proofId,
                secondSourceProofSha256 = proof.sha256(),
                secondScanAttestationId = scanId,
                secondScanAttestationSha256 = scan.sha256(),
            ),
        )
        return ReviewedRelease(release, firstProof, firstScan, proof, scan)
    }

    private companion object {
        const val REPOSITORY_ID = "seen-dev-registry-v1"
        const val ORIGIN = "https://seen.dev.yousef.codes/packages"
        const val VERSION = "1.2.3"
        const val NOW = "2026-07-17T00:00:00Z"
    }
}

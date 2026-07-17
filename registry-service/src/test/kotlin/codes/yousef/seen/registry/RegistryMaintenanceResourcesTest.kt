package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryMaintenanceResourcesTest {
    @Test
    fun `only publication modes construct the lease repository`() {
        RegistryMaintenanceMode.entries.forEach { mode ->
            var leaseRepositoryCreations = 0
            val resources = RegistryMaintenanceResources.create(
                config = maintenanceMemoryConfig(mode),
                leaseRepositoryFactory = {
                    leaseRepositoryCreations += 1
                    InMemoryRegistryRepository()
                },
                metadataStorageFactory = { maintenance ->
                    RestrictedMetadataRegistryObjectStorage(
                        allowImmutableCreates = maintenance.mode.allowImmutableMetadataCreates,
                        allowRootPointerWrite = maintenance.mode.allowRootPointerWrite,
                        allowTimestampPointerWrite = maintenance.mode.signingRoles.isNotEmpty(),
                    )
                },
                onlineSignersFactory = { maintenance, _ ->
                    publicOnlyTestSigners(maintenance.onlinePublicKeysHex)
                },
            )
            resources.use {
                assertEquals(mode.signingRoles, it.activeSigningRoles, mode.command)
                assertEquals(mode.requiresPublicationLease, it.hasPublicationLeaseRepository, mode.command)
            }
            assertEquals(if (mode.requiresPublicationLease) 1 else 0, leaseRepositoryCreations, mode.command)
        }
    }

    @Test
    fun `offline bootstrap construction never asks for a signer token or lease`() {
        var tokenRequests = 0
        var leaseRepositoryCreations = 0
        RegistryMaintenanceResources.create(
            config = maintenanceMemoryConfig(RegistryMaintenanceMode.IMPORT_OFFLINE_BOOTSTRAP),
            leaseRepositoryFactory = {
                leaseRepositoryCreations += 1
                InMemoryRegistryRepository()
            },
            remoteTokenProviderFactory = {
                tokenRequests += 1
                RemoteTufTokenProvider { "not-used" }
            },
        ).use { resources ->
            assertFalse(resources.hasPublicationLeaseRepository)
            assertEquals(emptySet(), resources.activeSigningRoles)
        }
        assertEquals(0, leaseRepositoryCreations)
        assertEquals(0, tokenRequests)
    }

    @Test
    fun `metadata storage denies package objects and ungranted pointer writes`() {
        val delegate = InMemoryRegistryObjectStorage()
        val importer = RestrictedMetadataRegistryObjectStorage(
            delegate = delegate,
            allowImmutableCreates = true,
            allowRootPointerWrite = true,
        )
        assertTrue(importer.putMetadataIfAbsent("1.root.json", "root".encodeToByteArray()))
        assertTrue(importer.replaceMetadataIfUnchanged("root.json", null, "root".encodeToByteArray()))
        assertFailsWith<IllegalArgumentException> {
            importer.replaceMetadataIfUnchanged("timestamp.json", null, "timestamp".encodeToByteArray())
        }
        assertFailsWith<IllegalStateException> { importer.putQuarantine("upload", byteArrayOf(1)) }
        assertFailsWith<IllegalStateException> { importer.getPublicBlob("a".repeat(64)) }

        val verifier = RestrictedMetadataRegistryObjectStorage(
            delegate = delegate,
            allowImmutableCreates = false,
            allowRootPointerWrite = false,
        )
        assertEquals("root", verifier.getMetadata("root.json")?.decodeToString())
        assertFailsWith<IllegalArgumentException> {
            verifier.putMetadataIfAbsent("2.root.json", "next".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            verifier.replaceMetadataIfUnchanged("root.json", "root".encodeToByteArray(), "next".encodeToByteArray())
        }
        assertFailsWith<IllegalArgumentException> { verifier.getMetadata("unrelated.json") }
    }
}

private fun publicOnlyTestSigners(keys: Map<String, String>): TufOnlineSigners {
    fun signer(role: String) = PublicKeyOnlyTufSigner(requireNotNull(keys[role]).hexToBytes())
    return TufOnlineSigners(
        releases = signer(TufRole.RELEASES),
        security = signer(TufRole.SECURITY),
        snapshot = signer(TufRole.SNAPSHOT),
        timestamp = signer(TufRole.TIMESTAMP),
    )
}

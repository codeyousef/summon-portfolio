package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceDeclarationTest {
    @Test
    fun `accepts supported forge declarations with full sha1 or sha256 commit ids`() {
        listOf("github", "gitlab").forEach { forge ->
            validSource(forge = forge).requireValidForReservation()
            validSource(forge = forge, expectedCommit = "b".repeat(64)).requireValidForReservation()
        }
        validSource(
            repositoryId = "r".repeat(128),
            installationId = "i".repeat(128),
            requestedRef = "r".repeat(255),
            licenseSpdx = "l".repeat(128),
        ).requireValidForReservation()
    }

    @Test
    fun `rejects unsupported forge out of bounds text and partial or malformed commits`() {
        val invalid = listOf(
            validSource(forge = "bitbucket"),
            validSource(repositoryId = ""),
            validSource(repositoryId = "r".repeat(129)),
            validSource(installationId = ""),
            validSource(installationId = "i".repeat(129)),
            validSource(requestedRef = ""),
            validSource(requestedRef = "r".repeat(256)),
            validSource(licenseSpdx = ""),
            validSource(licenseSpdx = "l".repeat(129)),
            validSource(expectedCommit = "a".repeat(39)),
            validSource(expectedCommit = "a".repeat(41)),
            validSource(expectedCommit = "a".repeat(63)),
            validSource(expectedCommit = "a".repeat(65)),
            validSource(expectedCommit = "A".repeat(40)),
            validSource(expectedCommit = "g".repeat(40)),
        )
        invalid.forEach { declaration ->
            val error = assertFailsWith<RegistryException> { declaration.requireValidForReservation() }
            assertEquals(400, error.status)
            assertEquals("invalid_request", error.code)
        }
    }

    private fun validSource(
        forge: String = "github",
        repositoryId: String = "123",
        installationId: String = "456",
        requestedRef: String = "refs/tags/v1.2.3",
        expectedCommit: String = "a".repeat(40),
        licenseSpdx: String = "MIT",
    ) = SourceDeclaration(forge, repositoryId, installationId, requestedRef, expectedCommit, licenseSpdx)
}

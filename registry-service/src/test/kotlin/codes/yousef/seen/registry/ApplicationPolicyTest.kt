package codes.yousef.seen.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationPolicyTest {
    @Test
    fun `signing policy description is bound to the selected environment`() {
        assertEquals(
            "seen-tuf-development-v1 root=offline:2/3 targets=offline:2/2 " +
                "releases=role-locked-signer:1/1 security=role-locked-signer:1/1 " +
                "snapshot=role-locked-signer:1/1 timestamp=role-locked-signer:1/1 " +
                "public-delay=259200 promotion=disabled",
            signingPolicyDescription("development"),
        )
        assertEquals(
            "seen-tuf-production-v1 root=offline:2/3 targets=offline:2/2 " +
                "releases=role-locked-signer:1/1 security=role-locked-signer:1/1 " +
                "snapshot=role-locked-signer:1/1 timestamp=role-locked-signer:1/1 " +
                "public-delay=disabled promotion=disabled writer=disabled mode=read-only",
            signingPolicyDescription("production"),
        )
        assertFailsWith<IllegalArgumentException> { signingPolicyDescription("staging") }
    }
}

package code.yousef.aws.jobs

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface JobClaim {
    data class Claimed(val claimToken: String, val leaseUntilMillis: Long) : JobClaim
    data object Complete : JobClaim
    data class Leased(val retryAfterMillis: Long) : JobClaim
    data object KeyReused : JobClaim
}

/**
 * Idempotency lease contract ported from the Durable Object (`/v1/idempotency/claim`,
 * `complete`, `abandon`): one active lease per key, a completed receipt retained for
 * [receiptTtlMillis], and a fingerprint that rejects reuse of a key for different work.
 */
interface JobLeaseStore {
    fun claim(key: String, fingerprint: String): JobClaim
    fun extend(key: String, fingerprint: String, claimToken: String): Boolean
    fun complete(key: String, fingerprint: String, claimToken: String): Boolean
    fun abandon(key: String, fingerprint: String, claimToken: String): Boolean
}

/**
 * Leases live in the sessions table (`pk` = "job:<key>") and share its `expires_at`
 * TTL attribute. All transitions are single conditional writes.
 */
class DynamoDbJobLeaseStore(
    private val client: DynamoDbClient,
    private val tableName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val leaseMillis: Long = DEFAULT_LEASE_MILLIS,
    private val receiptTtlMillis: Long = DEFAULT_RECEIPT_TTL_MILLIS,
) : JobLeaseStore {
    init {
        require(leaseMillis in 10_000..3_600_000) { "Job lease duration is out of range" }
    }

    override fun claim(key: String, fingerprint: String): JobClaim {
        requireFingerprint(fingerprint)
        val now = clock.millis()
        val token = UUID.randomUUID().toString()
        val leaseUntil = now + leaseMillis
        return try {
            client.putItem { request ->
                request.tableName(tableName)
                    .item(
                        key(key) + mapOf(
                            "state" to AttributeValue.fromS(LEASED),
                            "fingerprint" to AttributeValue.fromS(fingerprint),
                            "claim_token" to AttributeValue.fromS(token),
                            "lease_until" to AttributeValue.fromN(leaseUntil.toString()),
                            EXPIRES_AT to AttributeValue.fromN(seconds(leaseUntil).toString()),
                        ),
                    )
                    // Absent, TTL-expired, or an abandoned/expired lease for the same work.
                    .conditionExpression(
                        "attribute_not_exists(#pk) OR #expires <= :nowSeconds OR " +
                            "(#state = :leased AND #fingerprint = :fingerprint AND #leaseUntil <= :now)",
                    )
                    .expressionAttributeNames(
                        mapOf(
                            "#pk" to PK,
                            "#expires" to EXPIRES_AT,
                            "#state" to "state",
                            "#fingerprint" to "fingerprint",
                            "#leaseUntil" to "lease_until",
                        ),
                    )
                    .expressionAttributeValues(
                        mapOf(
                            ":nowSeconds" to AttributeValue.fromN(seconds(now).toString()),
                            ":now" to AttributeValue.fromN(now.toString()),
                            ":leased" to AttributeValue.fromS(LEASED),
                            ":fingerprint" to AttributeValue.fromS(fingerprint),
                        ),
                    )
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            }
            JobClaim.Claimed(token, leaseUntil)
        } catch (failure: ConditionalCheckFailedException) {
            val existing = failure.item().takeIf { failure.hasItem() }.orEmpty()
            when {
                existing.isEmpty() -> JobClaim.Leased(1_000L) // raced with a delete; retry shortly
                existing["fingerprint"]?.s() != fingerprint -> JobClaim.KeyReused
                existing["state"]?.s() == COMPLETE -> JobClaim.Complete
                else -> JobClaim.Leased(
                    ((existing["lease_until"]?.n()?.toLongOrNull() ?: (now + leaseMillis)) - now).coerceAtLeast(1_000L),
                )
            }
        }
    }

    override fun extend(key: String, fingerprint: String, claimToken: String): Boolean {
        val leaseUntil = clock.millis() + leaseMillis
        return conditionalUpdate(
            key = key,
            update = "SET #leaseUntil = :leaseUntil, #expires = :expires",
            values = mapOf(
                ":leaseUntil" to AttributeValue.fromN(leaseUntil.toString()),
                ":expires" to AttributeValue.fromN(seconds(leaseUntil).toString()),
            ),
            fingerprint = fingerprint,
            claimToken = claimToken,
        )
    }

    override fun complete(key: String, fingerprint: String, claimToken: String): Boolean {
        val now = clock.millis()
        return conditionalUpdate(
            key = key,
            update = "SET #state = :complete, #completedAt = :now, #leaseUntil = :zero, #expires = :expires REMOVE #token",
            values = mapOf(
                ":complete" to AttributeValue.fromS(COMPLETE),
                ":now" to AttributeValue.fromN(now.toString()),
                ":zero" to AttributeValue.fromN("0"),
                ":expires" to AttributeValue.fromN(seconds(now + receiptTtlMillis).toString()),
            ),
            fingerprint = fingerprint,
            claimToken = claimToken,
            extraNames = mapOf("#completedAt" to "completed_at"),
        )
    }

    override fun abandon(key: String, fingerprint: String, claimToken: String): Boolean = try {
        client.deleteItem { request ->
            request.tableName(tableName)
                .key(key(key))
                .conditionExpression("#state = :leased AND #fingerprint = :fingerprint AND #token = :token")
                .expressionAttributeNames(mapOf("#state" to "state", "#fingerprint" to "fingerprint", "#token" to "claim_token"))
                .expressionAttributeValues(
                    mapOf(
                        ":leased" to AttributeValue.fromS(LEASED),
                        ":fingerprint" to AttributeValue.fromS(fingerprint),
                        ":token" to AttributeValue.fromS(claimToken),
                    ),
                )
        }
        true
    } catch (_: ConditionalCheckFailedException) {
        false
    }

    private fun conditionalUpdate(
        key: String,
        update: String,
        values: Map<String, AttributeValue>,
        fingerprint: String,
        claimToken: String,
        extraNames: Map<String, String> = emptyMap(),
    ): Boolean = try {
        client.updateItem { request ->
            request.tableName(tableName)
                .key(key(key))
                .updateExpression(update)
                .conditionExpression("#state = :leased AND #fingerprint = :fingerprint AND #token = :token")
                .expressionAttributeNames(
                    mapOf(
                        "#state" to "state",
                        "#fingerprint" to "fingerprint",
                        "#token" to "claim_token",
                        "#leaseUntil" to "lease_until",
                        "#expires" to EXPIRES_AT,
                    ) + extraNames,
                )
                .expressionAttributeValues(
                    values + mapOf(
                        ":leased" to AttributeValue.fromS(LEASED),
                        ":fingerprint" to AttributeValue.fromS(fingerprint),
                        ":token" to AttributeValue.fromS(claimToken),
                    ),
                )
        }
        true
    } catch (_: ConditionalCheckFailedException) {
        false
    }

    private fun key(value: String): Map<String, AttributeValue> {
        require(JOB_KEY.matches(value)) { "Invalid job idempotency key" }
        return mapOf(PK to AttributeValue.fromS("job:$value"))
    }

    private fun seconds(millis: Long): Long = Math.floorDiv(millis, 1_000L)

    companion object {
        const val PK = "pk"
        const val EXPIRES_AT = "expires_at"
        const val DEFAULT_LEASE_MILLIS = 5L * 60 * 1_000
        const val DEFAULT_RECEIPT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1_000
        private const val LEASED = "leased"
        private const val COMPLETE = "complete"
        private val JOB_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9._:+-]{0,383}$")
    }
}

/** Process-local lease store for local development and tests. */
class InMemoryJobLeaseStore(private val clock: Clock = Clock.systemUTC()) : JobLeaseStore {
    private data class Lease(val fingerprint: String, val token: String?, val leaseUntil: Long, val complete: Boolean)
    private val leases = ConcurrentHashMap<String, Lease>()

    override fun claim(key: String, fingerprint: String): JobClaim {
        requireFingerprint(fingerprint)
        var result: JobClaim = JobClaim.KeyReused
        leases.compute(key) { _, existing ->
            val now = clock.millis()
            when {
                existing == null || (!existing.complete && existing.leaseUntil <= now && existing.fingerprint == fingerprint) -> {
                    val token = UUID.randomUUID().toString()
                    result = JobClaim.Claimed(token, now + DynamoDbJobLeaseStore.DEFAULT_LEASE_MILLIS)
                    Lease(fingerprint, token, now + DynamoDbJobLeaseStore.DEFAULT_LEASE_MILLIS, complete = false)
                }
                existing.fingerprint != fingerprint -> existing.also { result = JobClaim.KeyReused }
                existing.complete -> existing.also { result = JobClaim.Complete }
                else -> existing.also { result = JobClaim.Leased(existing.leaseUntil - now) }
            }
        }
        return result
    }

    override fun extend(key: String, fingerprint: String, claimToken: String): Boolean =
        transition(key, fingerprint, claimToken) { it.copy(leaseUntil = clock.millis() + DynamoDbJobLeaseStore.DEFAULT_LEASE_MILLIS) }

    override fun complete(key: String, fingerprint: String, claimToken: String): Boolean =
        transition(key, fingerprint, claimToken) { it.copy(token = null, leaseUntil = 0, complete = true) }

    override fun abandon(key: String, fingerprint: String, claimToken: String): Boolean =
        transition(key, fingerprint, claimToken) { null }

    private fun transition(key: String, fingerprint: String, token: String, next: (Lease) -> Lease?): Boolean {
        var applied = false
        leases.computeIfPresent(key) { _, existing ->
            if (!existing.complete && existing.fingerprint == fingerprint && existing.token == token) {
                applied = true
                next(existing)
            } else {
                existing
            }
        }
        return applied
    }
}

private fun requireFingerprint(fingerprint: String) {
    require(fingerprint.matches(Regex("^[a-f0-9]{64}$"))) { "Job fingerprint must be a SHA-256 digest" }
}

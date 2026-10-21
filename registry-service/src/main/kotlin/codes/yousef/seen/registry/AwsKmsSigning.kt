package codes.yousef.seen.registry

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.retries.DefaultRetryStrategy
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.KeyUsageType
import software.amazon.awssdk.services.kms.model.MessageType
import software.amazon.awssdk.services.kms.model.SignRequest
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import java.security.MessageDigest
import java.time.Duration

/**
 * One AWS KMS `ECC_NIST_EDWARDS25519` key used as a pure Ed25519 (RFC 8032)
 * TUF signer via `ED25519_SHA_512` with `MessageType=RAW`.
 *
 * The key is accepted only after `GetPublicKey` proves its spec, usage,
 * algorithm and raw public key equal the separately reviewed public key, and
 * every returned signature is verified locally before it can leave the
 * process. Any mismatch fails closed.
 *
 * KMS signs RAW messages of at most 4096 bytes. `ED25519_PH_SHA_512` (Ed25519ph)
 * is a different signature scheme that TUF Ed25519 clients do not verify, so
 * larger canonical metadata is rejected rather than pre-hashed.
 */
class AwsKmsEd25519Signer private constructor(
    private val client: KmsClient,
    private val keyArn: String,
    publicKey: ByteArray,
) : TufSigner {
    private val pinnedPublicKey = publicKey.copyOf()

    override val publicKey: ByteArray
        get() = pinnedPublicKey.copyOf()

    override fun sign(canonicalSignedBytes: ByteArray): ByteArray {
        if (canonicalSignedBytes.isEmpty() || canonicalSignedBytes.size > MAXIMUM_RAW_MESSAGE_BYTES) {
            throw TufSigningRequestException("Canonical metadata exceeds the AWS KMS Ed25519 RAW message bound")
        }
        val response = client.sign(
            SignRequest.builder()
                .keyId(keyArn)
                .message(SdkBytes.fromByteArray(canonicalSignedBytes))
                .messageType(MessageType.RAW)
                .signingAlgorithm(SigningAlgorithmSpec.ED25519_SHA_512)
                .build(),
        )
        check(response.keyId() == keyArn) { "AWS KMS signed with an unexpected key" }
        check(response.signingAlgorithm() == SigningAlgorithmSpec.ED25519_SHA_512) {
            "AWS KMS used an unexpected signing algorithm"
        }
        val signature = requireNotNull(response.signature()) { "AWS KMS returned no signature" }.asByteArray()
        check(signature.size == ED25519_SIGNATURE_BYTES) { "AWS KMS returned a non-Ed25519 signature" }
        check(verifyEd25519(pinnedPublicKey, canonicalSignedBytes, signature)) {
            "AWS KMS signature does not verify against the configured public key"
        }
        return signature
    }

    override fun close() = client.close()

    companion object {
        const val MAXIMUM_RAW_MESSAGE_BYTES = 4096

        /** Verifies the KMS key against [expectedPublicKey] before returning a signer. */
        fun create(client: KmsClient, keyArn: String, expectedPublicKey: ByteArray): AwsKmsEd25519Signer {
            require(expectedPublicKey.size == ED25519_PUBLIC_KEY_BYTES) {
                "AWS KMS Ed25519 public key must contain 32 raw bytes"
            }
            val response = client.getPublicKey(GetPublicKeyRequest.builder().keyId(keyArn).build())
            check(response.keyId() == keyArn) { "AWS KMS key ARN does not match its configured identity" }
            check(response.keySpec() == KeySpec.ECC_NIST_EDWARDS25519) { "AWS KMS key is not ECC_NIST_EDWARDS25519" }
            check(response.keyUsage() == KeyUsageType.SIGN_VERIFY) { "AWS KMS key is not a SIGN_VERIFY key" }
            check(SigningAlgorithmSpec.ED25519_SHA_512 in response.signingAlgorithms()) {
                "AWS KMS key does not support ED25519_SHA_512"
            }
            val actual = parseEd25519SubjectPublicKeyInfo(
                requireNotNull(response.publicKey()) { "AWS KMS returned no public key" }.asByteArray(),
            )
            check(MessageDigest.isEqual(actual, expectedPublicKey)) {
                "AWS KMS public key does not match the configured public key"
            }
            return AwsKmsEd25519Signer(client, keyArn, expectedPublicKey)
        }

        fun create(
            keyArn: String,
            region: String,
            expectedPublicKey: ByteArray,
            timeout: Duration,
        ): AwsKmsEd25519Signer {
            val client = KmsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(timeout).socketTimeout(timeout))
                .overrideConfiguration { override ->
                    override.apiCallTimeout(timeout)
                        .apiCallAttemptTimeout(timeout)
                        .retryStrategy(DefaultRetryStrategy.doNotRetry())
                }
                .build()
            return try {
                create(client, keyArn, expectedPublicKey)
            } catch (failure: Exception) {
                client.close()
                throw failure
            }
        }
    }
}

/**
 * Extracts the raw 32-byte key from a DER `SubjectPublicKeyInfo` for Ed25519
 * (RFC 8410). Ed25519 has exactly one valid encoding: algorithm OID
 * 1.3.101.112 with absent parameters and a 33-byte BIT STRING with zero
 * unused bits, so anything other than that exact 44-byte form is rejected.
 */
fun parseEd25519SubjectPublicKeyInfo(der: ByteArray): ByteArray {
    require(der.size == ED25519_SPKI_PREFIX.size + ED25519_PUBLIC_KEY_BYTES) {
        "Ed25519 SubjectPublicKeyInfo has an invalid length"
    }
    require(der.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)) {
        "SubjectPublicKeyInfo is not an Ed25519 public key"
    }
    return der.copyOfRange(ED25519_SPKI_PREFIX.size, der.size)
}

internal val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2a, // SEQUENCE, 42 bytes
    0x30, 0x05, // SEQUENCE AlgorithmIdentifier, 5 bytes
    0x06, 0x03, 0x2b, 0x65, 0x70, // OID 1.3.101.112 (id-Ed25519)
    0x03, 0x21, 0x00, // BIT STRING, 33 bytes, 0 unused bits
)

private const val ED25519_SIGNATURE_BYTES = 64

package codes.yousef.seen.registry

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.KeyUsageType
import software.amazon.awssdk.services.kms.model.MessageType
import software.amazon.awssdk.services.kms.model.SignRequest
import software.amazon.awssdk.services.kms.model.SignResponse
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AwsKmsSigningTest {
    @Test
    fun `JCA generated SubjectPublicKeyInfo parses to the raw Ed25519 key`() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val der = pair.public.encoded
        assertEquals(44, der.size)
        val raw = parseEd25519SubjectPublicKeyInfo(der)
        assertEquals(32, raw.size)
        // A JCA signature must verify under the parsed raw key with the
        // registry's own verifier, proving the extraction is the TUF key.
        val message = "canonical-tuf".encodeToByteArray()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(pair.private)
            update(message)
            sign()
        }
        assertTrue(verifyEd25519(raw, message, signature))
    }

    @Test
    fun `SubjectPublicKeyInfo parsing rejects every non Ed25519 encoding`() {
        val der = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded
        val x25519 = KeyPairGenerator.getInstance("X25519").generateKeyPair().public.encoded
        val p256 = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public.encoded
        listOf(
            der.copyOf(43),
            der + byteArrayOf(0),
            x25519,
            p256,
            der.copyOf().also { it[8] = 0x71 }, // Ed448 OID arc
            der.copyOf().also { it[11] = 0x01 }, // non-zero unused bits
        ).forEach { candidate ->
            assertFailsWith<IllegalArgumentException> { parseEd25519SubjectPublicKeyInfo(candidate) }
        }
    }

    @Test
    fun `RFC 8032 test vector signs through the KMS adapter`() {
        // RFC 8032 section 7.1, TEST 2 (one-byte message 0x72).
        val seed = "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb".hexToBytes()
        val publicKey = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c".hexToBytes()
        val expected = ("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da" +
            "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00").hexToBytes()
        val kms = FakeKms(seed)
        val signer = AwsKmsEd25519Signer.create(kms, KEY_ARN, publicKey)
        assertContentEquals(publicKey, signer.publicKey)
        assertContentEquals(expected, signer.sign(byteArrayOf(0x72)))
        val request = kms.signRequests.single()
        assertEquals(KEY_ARN, request.keyId())
        assertEquals(MessageType.RAW, request.messageType())
        assertEquals(SigningAlgorithmSpec.ED25519_SHA_512, request.signingAlgorithm())
    }

    @Test
    fun `startup fails closed unless KMS proves the pinned key`() {
        val seed = ByteArray(32) { (it + 3).toByte() }
        val publicKey = Ed25519PrivateKeyParameters(seed).generatePublicKey().encoded
        val other = Ed25519PrivateKeyParameters(ByteArray(32) { 9 }).generatePublicKey().encoded
        assertFailsWith<IllegalStateException> { AwsKmsEd25519Signer.create(FakeKms(seed), KEY_ARN, other) }
        assertFailsWith<IllegalStateException> {
            AwsKmsEd25519Signer.create(FakeKms(seed, keySpec = KeySpec.ECC_NIST_P256), KEY_ARN, publicKey)
        }
        assertFailsWith<IllegalStateException> {
            AwsKmsEd25519Signer.create(FakeKms(seed, keyUsage = KeyUsageType.ENCRYPT_DECRYPT), KEY_ARN, publicKey)
        }
        assertFailsWith<IllegalStateException> {
            AwsKmsEd25519Signer.create(
                FakeKms(seed, algorithms = listOf(SigningAlgorithmSpec.ED25519_PH_SHA_512)),
                KEY_ARN,
                publicKey,
            )
        }
        assertFailsWith<IllegalStateException> {
            AwsKmsEd25519Signer.create(FakeKms(seed, reportedArn = KEY_ARN.replace("0f1e", "aaaa")), KEY_ARN, publicKey)
        }
    }

    @Test
    fun `signatures that do not verify or exceed the RAW bound never leave the signer`() {
        val seed = ByteArray(32) { (it + 5).toByte() }
        val publicKey = Ed25519PrivateKeyParameters(seed).generatePublicKey().encoded
        val substituted = FakeKms(seed, signingSeed = ByteArray(32) { 1 })
        val signer = AwsKmsEd25519Signer.create(substituted, KEY_ARN, publicKey)
        assertFailsWith<IllegalStateException> { signer.sign("metadata".encodeToByteArray()) }

        val honest = AwsKmsEd25519Signer.create(FakeKms(seed), KEY_ARN, publicKey)
        assertEquals(64, honest.sign(ByteArray(4096) { 1 }).size)
        assertFailsWith<TufSigningRequestException> { honest.sign(ByteArray(4097)) }
        assertFailsWith<IllegalStateException> {
            AwsKmsEd25519Signer.create(FakeKms(seed, algorithmInResponse = SigningAlgorithmSpec.ED25519_PH_SHA_512), KEY_ARN, publicKey)
                .sign(byteArrayOf(1))
        }
    }

    private class FakeKms(
        seed: ByteArray,
        private val keySpec: KeySpec = KeySpec.ECC_NIST_EDWARDS25519,
        private val keyUsage: KeyUsageType = KeyUsageType.SIGN_VERIFY,
        private val algorithms: List<SigningAlgorithmSpec> = listOf(
            SigningAlgorithmSpec.ED25519_SHA_512,
            SigningAlgorithmSpec.ED25519_PH_SHA_512,
        ),
        private val reportedArn: String = KEY_ARN,
        signingSeed: ByteArray = seed,
        private val algorithmInResponse: SigningAlgorithmSpec = SigningAlgorithmSpec.ED25519_SHA_512,
    ) : KmsClient {
        private val publicKey = Ed25519PrivateKeyParameters(seed).generatePublicKey().encoded
        private val signingKey = Ed25519PrivateKeyParameters(signingSeed)
        val signRequests = mutableListOf<SignRequest>()

        override fun getPublicKey(request: GetPublicKeyRequest): GetPublicKeyResponse = GetPublicKeyResponse.builder()
            .keyId(reportedArn)
            .keySpec(keySpec)
            .keyUsage(keyUsage)
            .signingAlgorithms(algorithms)
            .publicKey(SdkBytes.fromByteArray(ED25519_SPKI_PREFIX + publicKey))
            .build()

        override fun sign(request: SignRequest): SignResponse {
            signRequests += request
            val message = request.message().asByteArray()
            val signature = Ed25519Signer().run {
                init(true, signingKey)
                update(message, 0, message.size)
                generateSignature()
            }
            return SignResponse.builder()
                .keyId(reportedArn)
                .signingAlgorithm(algorithmInResponse)
                .signature(SdkBytes.fromByteArray(signature))
                .build()
        }

        override fun serviceName(): String = "kms"
        override fun close() = Unit
    }

    private companion object {
        const val KEY_ARN = "arn:aws:kms:eu-north-1:123456789012:key/0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"
    }
}

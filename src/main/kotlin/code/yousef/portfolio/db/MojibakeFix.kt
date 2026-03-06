package code.yousef.portfolio.db

/**
 * Fix mojibake from Aether admin's byte-by-byte URL decoder.
 * The decoder treats each %XX as a Latin-1 char instead of grouping multi-byte UTF-8 sequences,
 * so e.g. 関 (UTF-8: E9 96 A2) becomes é\u0096¢. Re-encoding as Latin-1 recovers the original
 * UTF-8 bytes, which we then decode correctly.
 */
fun fixMojibake(s: String): String {
    if (s.all { it.code < 128 }) return s       // Pure ASCII — nothing to fix
    if (s.any { it.code > 255 }) return s        // Already valid Unicode, not mojibake
    return try {
        val bytes = s.toByteArray(Charsets.ISO_8859_1)
        val fixed = String(bytes, Charsets.UTF_8)
        if (fixed.contains('\uFFFD')) s else fixed
    } catch (_: Exception) {
        s
    }
}

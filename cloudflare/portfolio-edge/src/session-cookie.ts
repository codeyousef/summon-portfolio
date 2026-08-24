const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{32}$/;

export function replaceSessionCookie(headers: Headers, cookieName: string, sessionId: string | null): void {
  if (sessionId !== null && !SESSION_ID_PATTERN.test(sessionId)) throw new Error("Invalid edge session id");
  const cookies = headers.get("cookie")?.split(";") ?? [];
  const retained = cookies
    .map((cookie) => cookie.trim())
    .filter((cookie) => {
      if (cookie.length === 0) return false;
      const separator = cookie.indexOf("=");
      return separator < 0 || cookie.slice(0, separator).trim() !== cookieName;
    });
  if (sessionId !== null) retained.push(`${cookieName}=${sessionId}`);
  if (retained.length === 0) headers.delete("cookie"); else headers.set("cookie", retained.join("; "));
}

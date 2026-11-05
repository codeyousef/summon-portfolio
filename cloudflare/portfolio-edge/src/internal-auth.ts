export async function authorizedInternalRequest(request: Request, expectedToken: string): Promise<boolean> {
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ") || expectedToken.length < 32) return false;
  const [actual, expected] = await Promise.all([
    crypto.subtle.digest("SHA-256", new TextEncoder().encode(authorization.slice(7))),
    crypto.subtle.digest("SHA-256", new TextEncoder().encode(expectedToken)),
  ]);
  const left = new Uint8Array(actual);
  const right = new Uint8Array(expected);
  let difference = left.length ^ right.length;
  for (let index = 0; index < Math.min(left.length, right.length); index += 1) difference |= left[index]! ^ right[index]!;
  return difference === 0;
}

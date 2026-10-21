// Resolves the edge worker's extensionless relative imports to .ts files and rewrites the
// single TypeScript parameter property (unsupported by Node's type stripping) in memory.
export async function resolve(specifier, context, next) {
  try {
    return await next(specifier, context);
  } catch (error) {
    if (specifier.startsWith(".") && !specifier.endsWith(".ts")) return next(`${specifier}.ts`, context);
    throw error;
  }
}

export async function load(url, context, next) {
  const result = await next(url, context);
  if (!url.endsWith("/connector-failure.ts")) return result;
  const source = String(result.source);
  const rewritten = source
    .replace(
      "constructor(\n    readonly failureCode: ConnectorFailureCode,",
      "readonly failureCode: ConnectorFailureCode;\n  constructor(\n    failureCode: ConnectorFailureCode,",
    )
    .replace(
      "super(message);\n    this.name = \"ConnectorFailure\";",
      "super(message);\n    this.failureCode = failureCode;\n    this.name = \"ConnectorFailure\";",
    );
  if (rewritten === source) throw new Error("connector-failure.ts rewrite did not apply");
  return { ...result, source: rewritten };
}

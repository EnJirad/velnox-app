/**
 * Build-configuration guard.
 *
 * A small, dependency-free check for the invariants that are easy to break by hand and
 * expensive to discover in CI:
 *
 *  1. every `applicationId` in the repository is unique (three apps must install side
 *     by side, and two identical ids would make an APK silently replace another);
 *  2. every `applicationId` matches the `com.velnox.<app>` convention;
 *  3. the Gradle version catalog parses and every `version.ref` resolves;
 *  4. no backend secret name appears anywhere in the shipped sources.
 *
 * Run: `bun tools/verify-android-config.ts` (also compiled by `tsc -b --noEmit`).
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = process.cwd();

/** Secret names that must never exist in this repository (see ANDROID_BUILD.md § Signing). */
const FORBIDDEN_SECRETS = [
  "DATABASE_URL",
  "DATABASE_PASSWORD",
  "POSTGRES_PASSWORD",
  "JWT_SECRET",
  "GOOGLE_CLIENT_SECRET",
  "R2_ACCOUNT_ID",
  "R2_ACCESS_KEY_ID",
  "R2_SECRET_ACCESS_KEY",
  "BOOTSTRAP_OWNER_SECRET",
  "NEON_DATABASE_URL",
];

/**
 * Whether a file may mention a secret name at all.
 *
 * Markdown is exempt wholesale rather than file by file: documentation exists to say
 * "these names must not appear here", and an allowlist that has to be extended every
 * time a doc mentions a secret is an allowlist people eventually delete. Markdown is
 * never compiled into an APK, so it cannot leak anything.
 *
 * `.kts`/`.kt`/`.xml` files stay fully checked — those are exactly the files that can
 * carry a value into a build.
 */
function mayMentionSecretNames(relativePath: string): boolean {
  return relativePath.endsWith(".md") || relativePath === "tools/verify-android-config.ts";
}

interface Catalogue {
  readonly versions: ReadonlySet<string>;
  readonly references: readonly string[];
}

function readCatalogue(): Catalogue {
  const raw = readFileSync(join(ROOT, "gradle", "libs.versions.toml"), "utf8");
  const versions = new Set<string>();
  const references: string[] = [];
  let section = "";

  for (const line of raw.split("\n")) {
    const trimmed = line.trim();
    if (trimmed.startsWith("[")) {
      section = trimmed;
      continue;
    }
    if (trimmed === "" || trimmed.startsWith("#")) continue;

    if (section === "[versions]") {
      versions.add(trimmed.split("=")[0].trim());
    } else if (section === "[libraries]" || section === "[plugins]") {
      const match = /version\.ref\s*=\s*"([^"]+)"/.exec(trimmed);
      if (match?.[1] !== undefined) references.push(match[1]);
    }
  }

  return { versions, references };
}

function walk(directory: string, filter: (path: string) => boolean): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(directory)) {
    if (entry === ".git" || entry === "node_modules" || entry === "build") continue;
    const full = join(directory, entry);
    if (statSync(full).isDirectory()) found.push(...walk(full, filter));
    else if (filter(full)) found.push(full);
  }
  return found;
}

function readApplicationIds(): Map<string, string> {
  const ids = new Map<string, string>();
  for (const file of walk(join(ROOT, "app"), (path) => path.endsWith("build.gradle.kts"))) {
    const source = readFileSync(file, "utf8");
    const match = /applicationId\s*=\s*"([^"]+)"/.exec(source);
    if (match?.[1] !== undefined) ids.set(relative(ROOT, file), match[1]);
  }
  return ids;
}

function collectViolations(): string[] {
  const violations: string[] = [];

  const catalogue = readCatalogue();
  for (const reference of catalogue.references) {
    if (!catalogue.versions.has(reference)) {
      violations.push(`libs.versions.toml: version.ref "${reference}" is not declared in [versions]`);
    }
  }

  const ids = readApplicationIds();
  const seen = new Map<string, string>();
  for (const [file, id] of ids) {
    const previous = seen.get(id);
    if (previous !== undefined) {
      violations.push(`duplicate applicationId "${id}" in ${file} and ${previous}`);
    }
    seen.set(id, file);
    if (!/^com\.velnox\.[a-z]+$/.test(id)) {
      violations.push(`${file}: applicationId "${id}" does not match com.velnox.<app>`);
    }
  }

  const sources = walk(ROOT, (path) => path.endsWith(".kt") || path.endsWith(".kts") || path.endsWith(".xml"));
  for (const file of sources) {
    const relativePath = relative(ROOT, file);
    if (mayMentionSecretNames(relativePath)) continue;
    // Comments are stripped first: naming a secret in order to document that it must
    // NOT be present is the point of several files here, and flagging that would train
    // everyone to ignore this check. Only a live reference in code is a violation.
    const code = stripComments(readFileSync(file, "utf8"));
    for (const secret of FORBIDDEN_SECRETS) {
      if (code.includes(secret)) {
        violations.push(`${relativePath}: references forbidden secret name "${secret}" in code`);
      }
    }
  }

  return violations;
}

/** Removes `//` line comments and `/* … *​/` block comments, preserving line structure. */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/<!--[\s\S]*?-->/g, "")
    .split("\n")
    .map((line) => line.replace(/\/\/.*$/, ""))
    .join("\n");
}

const problems = collectViolations();

if (problems.length > 0) {
  console.error("Velnox Android configuration check FAILED:");
  for (const problem of problems) console.error(`  ✗ ${problem}`);
  process.exit(1);
}

console.log("Velnox Android configuration check passed.");
console.log("  · version catalog resolves");
console.log("  · applicationIds unique and namespaced");
console.log("  · no backend secret names in shipped sources");

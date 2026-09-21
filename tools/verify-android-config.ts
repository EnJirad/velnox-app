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
 *  4. no backend secret name appears anywhere in the shipped sources;
 *  5. the Google sign-in configuration is actually usable — a well-formed web
 *     client id is configured, and the code path that turns it into a Credential
 *     Manager `serverClientId` and exchanges the resulting ID token with the
 *     backend is still intact.
 *
 * Check 5 exists because its failure mode is silent. An APK with no client id
 * compiles, installs, passes lint and passes every unit test; it only reports that
 * sign-in is unavailable once a real user taps the button. Nothing downstream of
 * this script would notice, so without this check a build that cannot sign anyone
 * in still reports success.
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

/**
 * A Google **web** OAuth client id: `<project number>-<hash>.apps.googleusercontent.com`.
 *
 * A public identifier, not a credential — it is visible in the OAuth redirect URL
 * and in the web bundles, and the PKCE/code exchange that would need a secret happens
 * server-side. Only this shape reaches an APK.
 */
const GOOGLE_WEB_CLIENT_ID_PATTERN = /^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$/;

/** Reads one `key=value` entry out of a `.properties` file. */
function readPropertiesEntry(path: string, key: string): string {
  for (const line of readFileIfPresent(path).split("\n")) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
    const separator = trimmed.indexOf("=");
    if (separator === -1) continue;
    if (trimmed.slice(0, separator).trim() !== key) continue;
    return trimmed.slice(separator + 1).trim();
  }
  return "";
}

function readFileIfPresent(path: string): string {
  try {
    return readFileSync(join(ROOT, path), "utf8");
  } catch {
    return "";
  }
}

/**
 * Whether a path holds a JKS keystore.
 *
 * A JKS file begins with the magic `0xFEEDFEED`, so the file can be identified without
 * decoding it and without putting any of it — or any password — into a log. An absent
 * file is `false`, which is the failure this exists to catch.
 */
function isJksKeystore(path: string): boolean {
  try {
    const bytes = readFileSync(join(ROOT, path));
    return bytes.length > 4 && bytes.readUInt32BE(0) === 0xfeedfeed;
  } catch {
    return false;
  }
}

/**
 * The web client id the build will actually compile in.
 *
 * CI passes `-Pvelnox.google.webClientId="$VELNOX_GOOGLE_WEB_CLIENT_ID"`, so a
 * non-blank environment value wins and anything else falls through to the checked-in
 * default — the same order `gradle/velnox-properties.gradle.kts` implements.
 */
function effectiveGoogleWebClientId(): string {
  const fromEnvironment = process.env.VELNOX_GOOGLE_WEB_CLIENT_ID?.trim() ?? "";
  return fromEnvironment !== ""
    ? fromEnvironment
    : readPropertiesEntry("gradle.properties", "velnox.google.webClientId");
}

/** Records a violation when a required file is missing or stops matching `pattern`. */
function expectMatch(path: string, pattern: RegExp, description: string, violations: string[]): void {
  const source = readFileIfPresent(path);
  if (source === "") {
    violations.push(`${path} is missing`);
    return;
  }
  if (!pattern.test(source)) violations.push(`${path}: ${description}`);
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

  // ── Google sign-in must actually be usable ──────────────────────────────────
  // The most likely cause of "sign-in does not work" is configuration rather than
  // code, so configuration is checked first. The value itself is never printed.
  const googleWebClientId = effectiveGoogleWebClientId();
  if (googleWebClientId === "") {
    violations.push(
      "Google sign-in is not configured: gradle.properties has no velnox.google.webClientId " +
        "and VELNOX_GOOGLE_WEB_CLIENT_ID is unset, so every build would report that sign-in is unavailable",
    );
  } else if (!GOOGLE_WEB_CLIENT_ID_PATTERN.test(googleWebClientId)) {
    violations.push(
      "the effective Google web client id is not a <project-number>-<hash>.apps.googleusercontent.com value",
    );
  }

  expectMatch(
    "core/auth/build.gradle.kts",
    /buildConfigField\(\s*"String"\s*,\s*"VELNOX_GOOGLE_WEB_CLIENT_ID"/,
    "no longer compiles velnox.google.webClientId into BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID",
    violations,
  );

  expectMatch(
    "core/auth/src/main/kotlin/com/velnox/core/auth/signin/NativeGoogleSignIn.kt",
    /setServerClientId\(\s*webClientId\s*\)/,
    "no longer passes the web client id as the Credential Manager serverClientId, so the " +
      "returned token's aud would not match the backend's GOOGLE_CLIENT_ID",
    violations,
  );

  expectMatch(
    "core/auth/src/main/kotlin/com/velnox/core/auth/api/VelnoxAuthApi.kt",
    /@POST\("auth\/native\/google"\)/,
    "no longer declares POST auth/native/google, the backend contract a native Google ID token is exchanged on",
    violations,
  );

  expectMatch(
    "core/auth/src/main/kotlin/com/velnox/core/auth/signin/NativeGoogleSignIn.kt",
    /\.setNonce\(nonce\)/,
    "no longer sets an OIDC nonce on the credential request, so a Google ID token would not be bound to the sign-in attempt",
    violations,
  );

  expectMatch(
    "core/auth/src/main/kotlin/com/velnox/core/auth/signin/NativeGoogleSignIn.kt",
    /\.setFilterByAuthorizedAccounts\(false\)/,
    "no longer disables the authorized-accounts filter, which makes Google return nothing on a first sign-in",
    violations,
  );

  expectMatch(
    "core/auth/src/main/kotlin/com/velnox/core/auth/repository/AuthRepository.kt",
    /NativeGoogleLoginRequest\(idToken = idToken, nonce = nonce\)/,
    "no longer sends the nonce with the ID token, so the backend could not confirm the token was minted for this attempt",
    violations,
  );

  // ── The debug signing key must not move ─────────────────────────────────────
  // Google authorises a Credential Manager sign-in request from the (package name,
  // signing certificate SHA-1) pair, so a debug key that changes between builds makes a
  // registered Android OAuth client permanently stale: the account chooser returns
  // nothing and the user is told sign-in failed. That is exactly what shipped — two
  // consecutive CI runs signed com.velnox.velshop.debug with 21:7C:CD:93… and then
  // A5:59:21:6F… — and the cause was a workflow that generated a fresh key every run.
  // A rotation, or a silent detach of the committed key from the build, is a real
  // regression of the sign-in flow, so it fails here rather than in the chooser.
  const debugKeystore = "signing/velnox-debug.keystore";
  if (!isJksKeystore(debugKeystore)) {
    violations.push(
      `${debugKeystore} is missing or is not a JKS keystore, so the debug APKs would be ` +
        "signed by a key the build machine generates, and no Android OAuth client could stay registered",
    );
  }

  for (const app of ["velshop", "velseller", "velcenter"]) {
    const modulePath = `app/${app}/build.gradle.kts`;
    const source = stripComments(readFileIfPresent(modulePath));
    if (!source.includes(`rootProject.file("${debugKeystore}")`)) {
      violations.push(
        `${modulePath}: the debug signing config no longer points at ${debugKeystore}, so this ` +
          "app's certificate would diverge from the one registered with Google",
      );
    }
    if (!/debug\s*\{[^}]*signingConfig\s*=/.test(source)) {
      violations.push(
        `${modulePath}: the debug build type no longer sets a signingConfig, so AGP would sign ` +
          "with whichever debug keystore the machine happens to have",
      );
    }
  }

  const workflowSource = stripComments(readFileIfPresent(".github/workflows/build-android.yml"));
  if (workflowSource.includes("keytool -genkeypair")) {
    violations.push(
      ".github/workflows/build-android.yml generates a signing key, so the debug certificate " +
        "— and the SHA-1 an Android OAuth client must be registered against — would change on every run",
    );
  }
  if (!workflowSource.includes("verify --print-certs")) {
    violations.push(
      ".github/workflows/build-android.yml no longer reads the certificate back out of the signed " +
        "APKs, so the SHA-1 reported for Google registration could drift from the one the shipped " +
        "APK is actually signed with",
    );
  }

  // The client id belongs in gradle.properties (the public checked-in default) or in
  // the VELNOX_GOOGLE_WEB_CLIENT_ID repository variable. A literal in the workflow is
  // the kind of duplicate that silently drifts from the real project.
  const workflow = stripComments(readFileIfPresent(".github/workflows/build-android.yml"));
  if (workflow === "") {
    violations.push(".github/workflows/build-android.yml is missing");
  } else {
    if (/\d+-[a-z0-9]+\.apps\.googleusercontent\.com/.test(workflow)) {
      violations.push(
        ".github/workflows/build-android.yml hardcodes a Google client id; it must come from " +
          "gradle.properties or the VELNOX_GOOGLE_WEB_CLIENT_ID repository variable",
      );
    }
    if (!workflow.includes("-Pvelnox.google.webClientId")) {
      violations.push(
        ".github/workflows/build-android.yml does not pass -Pvelnox.google.webClientId, " +
          "so CI-built APKs would carry no client id unless the checked-in default covers it",
      );
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
console.log("  · Google sign-in configured, serverClientId wired, native/google contract present");

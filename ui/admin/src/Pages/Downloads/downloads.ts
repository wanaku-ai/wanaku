/**
 * Static, front-end model of the CLI packages that Wanaku publishes.
 *
 * Wanaku ships its CLI in more than one flavour: a self-contained native
 * binary for a specific platform and a portable Java package that runs on any
 * operating system that has a compatible JVM installed. A single download link
 * cannot represent all of those options, so the page below lists every package
 * and lets the user pick the one that matches their environment.
 *
 * The CLI is released from the companion `wanaku-barn` repository. The entries
 * are intentionally kept as a plain, extensible array: adding support for new
 * platforms (for example native macOS or Linux aarch64 builds) is just a matter
 * of adding new objects to {@link CLI_DOWNLOADS}.
 */

/** Distinguishes portable Java packages from platform-specific native binaries. */
export type CliPackageKind = "native" | "java";

export interface CliDownload {
  /** Stable identifier, also used as the table row id and in tests. */
  id: string;
  /** Human friendly package name. */
  name: string;
  /** Whether the package is a native binary or a Java (JVM) package. */
  kind: CliPackageKind;
  /** The runtime the package needs, e.g. "Native (no runtime required)". */
  runtime: string;
  /** The supported operating system / architecture. */
  platform: string;
  /** The name of the artifact as published in the release. */
  fileName: string;
  /** Direct download URL for the artifact. */
  downloadUrl: string;
  /** Optional extra guidance shown to the user. */
  notes?: string;
}

/** The GitHub repository that publishes the Wanaku CLI packages. */
export const CLI_REPO = "wanaku-ai/wanaku-barn";

/**
 * The version of the CLI packages. Keep this in sync with the Wanaku project
 * version (see the workspace `Cargo.toml` and `ui/admin/package.json`).
 */
export const CLI_VERSION = "0.3.0";

/**
 * The release channel the downloads point at.
 *
 * - `early-access` tracks the latest pre-release build. It is published under
 *   the fixed `early-access` tag, and its artifacts carry the `-SNAPSHOT`
 *   suffix (for example `wanaku-cli-0.3.0-SNAPSHOT.zip`).
 * - `stable` points at the tagged release for {@link CLI_VERSION}. It is
 *   published under the `v<version>` tag (for example `v0.3.0`), and its
 *   artifacts use the plain version (for example `wanaku-cli-0.3.0.zip`).
 */
export type ReleaseChannel = "early-access" | "stable";
export const CLI_RELEASE_CHANNEL: ReleaseChannel = "early-access";

const isEarlyAccess = CLI_RELEASE_CHANNEL === "early-access";

/**
 * Release tag the downloads point at. Derived from the selected channel so the
 * links stay correct for both early-access and stable releases.
 */
export const CLI_RELEASE_TAG = isEarlyAccess ? "early-access" : `v${CLI_VERSION}`;

/** The artifact version used in the published file names for the channel. */
const ARTIFACT_VERSION = isEarlyAccess ? `${CLI_VERSION}-SNAPSHOT` : CLI_VERSION;

/** Page that lists every asset of the referenced release. */
export const CLI_RELEASE_PAGE = `https://github.com/${CLI_REPO}/releases/tag/${CLI_RELEASE_TAG}`;

const downloadUrl = (fileName: string): string =>
  `https://github.com/${CLI_REPO}/releases/download/${CLI_RELEASE_TAG}/${fileName}`;

/**
 * The list of CLI packages available for download.
 *
 * Add new native builds (macOS, Linux aarch64, Windows, ...) here as they
 * become available in the release.
 */
export const CLI_DOWNLOADS: CliDownload[] = [
  {
    id: "java-universal",
    name: "Wanaku CLI (Java)",
    kind: "java",
    runtime: "Java 21+",
    platform: "Any (JVM)",
    fileName: `wanaku-cli-${ARTIFACT_VERSION}.zip`,
    downloadUrl: downloadUrl(`wanaku-cli-${ARTIFACT_VERSION}.zip`),
    notes: "Portable package. Requires a Java 21+ runtime installed on your machine.",
  },
  {
    id: "native-linux-x86_64",
    name: "Wanaku CLI (native)",
    kind: "native",
    runtime: "Native (no runtime required)",
    platform: "Linux x86_64",
    fileName: `wanaku-cli-${ARTIFACT_VERSION}-linux-x86_64.zip`,
    downloadUrl: downloadUrl(`wanaku-cli-${ARTIFACT_VERSION}-linux-x86_64.zip`),
    notes: "Self-contained binary. Unzip, make it executable and move it into your PATH.",
  },
];

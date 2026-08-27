/**
 * Static catalog of Wanaku CLI packages available for download.
 *
 * The Wanaku CLI is published from the wanaku-barn repository and works with
 * both the Java-based "Classic" router and this Rust-based router. New
 * package types (e.g. additional native platforms) can be added here without
 * touching the page component.
 */

export type CliPackageType = "native" | "java";

export interface CliPackage {
  /** Stable identifier, used as the React list key. */
  id: string;
  /** Display name shown on the card. */
  name: string;
  /** Distinguishes native binaries from Java archives that need a JVM. */
  packageType: CliPackageType;
  /** Supported platform(s) or runtime for this package. */
  platform: string;
  /** Extra runtime requirement, if any (e.g. "Requires Java 17 or later"). */
  requirement?: string;
  /** Where to download the package or view release assets. */
  downloadUrl: string;
  /** Short description of the package. */
  description: string;
}

export const CLI_PACKAGE_TYPE_LABELS: Record<CliPackageType, string> = {
  native: "Native Binaries",
  java: "Java Packages",
};

export const CLI_RELEASES_URL =
  "https://github.com/wanaku-ai/wanaku-barn/releases/tag/early-access";

export const CLI_PACKAGES: CliPackage[] = [
  {
    id: "cli-native-linux-x86_64",
    name: "Wanaku CLI",
    packageType: "native",
    platform: "Linux (x86_64)",
    downloadUrl: CLI_RELEASES_URL,
    description:
      "Self-contained native binary. Does not require a Java runtime.",
  },
  {
    id: "cli-java",
    name: "Wanaku CLI",
    packageType: "java",
    platform: "Linux, macOS, Windows",
    requirement: "Requires Java 17 or later",
    downloadUrl: CLI_RELEASES_URL,
    description:
      "Cross-platform archive. Runs anywhere a compatible JVM is installed.",
  },
];

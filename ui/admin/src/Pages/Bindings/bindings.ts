import { CredentialBinding, InjectionMechanism } from "../../models";

export function sortedBindings(bindings: readonly CredentialBinding[]): CredentialBinding[] {
  const result = [...bindings];
  result.sort((a, b) => {
    const byForward = a.forwardId.localeCompare(b.forwardId);
    if (byForward !== 0) return byForward;
    return a.id.localeCompare(b.id);
  });
  return result;
}

export function formatMechanism(mechanism: InjectionMechanism | undefined): string {
  if (!mechanism) return "Unknown";
  switch (mechanism.type) {
    case "bearer":
      return "Bearer token";
    case "named_header":
      return mechanism.header ? `Named header (${mechanism.header})` : "Named header";
    case "basic":
      return "Basic auth";
    default:
      return "Unknown";
  }
}

import type {
  ActionPolicy,
  ActionPolicyRevision,
  ActionPolicyRule,
} from '../../hooks/api/use-action-policies';

// Object key order does not change a rule. Array order is preserved.
const canonicalJson = (value: unknown) =>
  JSON.stringify(value, (_key, entry: unknown) =>
    entry && typeof entry === 'object' && !Array.isArray(entry)
      ? Object.fromEntries(Object.entries(entry).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
      : entry,
  );

// A rejected policy can contain more than one rule with the same ID.
const groupRules = (policy: ActionPolicy) => {
  const byId = new Map<string, ActionPolicyRule[]>();
  for (const rule of policy.rules ?? []) {
    const matches = byId.get(rule.id);
    if (matches) matches.push(rule);
    else byId.set(rule.id, [rule]);
  }
  return byId;
};

const formatRules = (rules: ActionPolicyRule[]) =>
  JSON.stringify(rules.length === 1 ? rules[0] : rules, null, 2);

export const PolicyComparison = ({
  active,
  policy,
}: {
  active: ActionPolicyRevision;
  policy: ActionPolicy;
}) => {
  const activeRules = groupRules(active.policy);
  const selectedRules = groupRules(policy);
  const ids = [...new Set([...activeRules.keys(), ...selectedRules.keys()])];
  const changes = ids.flatMap((id) => {
    const before = activeRules.get(id);
    const after = selectedRules.get(id);
    if (canonicalJson(before) === canonicalJson(after)) return [];
    return [{ id, before, after, kind: !before ? 'Added' : !after ? 'Removed' : 'Modified' }];
  });

  return (
    <section aria-label="Rule comparison">
      <h4>Comparison with active revision {active.revision.id}</h4>
      {changes.length === 0 ? (
        <p>No rule changes.</p>
      ) : (
        changes.map(({ id, kind, before, after }) => (
          <details key={id}>
            <summary>
              {kind} — {id}
            </summary>
            {before && (
              <>
                <p>Active revision</p>
                <pre className="policy-json">{formatRules(before)}</pre>
              </>
            )}
            {after && (
              <>
                <p>Selected revision</p>
                <pre className="policy-json">{formatRules(after)}</pre>
              </>
            )}
          </details>
        ))
      )}
    </section>
  );
};

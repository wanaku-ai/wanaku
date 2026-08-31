import { Accordion, AccordionItem, StructuredListBody, StructuredListCell, StructuredListRow, StructuredListWrapper, Tag } from "@carbon/react";
import type { ActionPolicy, ActionPolicyRule, PolicyPredicate } from "../../hooks/api/use-action-policies";

interface PolicyDetailsProps {
  policy: ActionPolicy;
}

const displayJson = (value: unknown): string => value === undefined ? "—" : JSON.stringify(value);

const selectorEntries = (rule: ActionPolicyRule): Array<[string, string]> => {
  const selectors = rule.selectors ?? {};
  const entries: Array<[string, string]> = [];
  if (selectors.namespace) entries.push(["Namespace", selectors.namespace]);
  if (selectors.operation) entries.push(["Operation", selectors.operation]);
  if (selectors.target_type) entries.push(["Target type", selectors.target_type]);
  if (selectors.target_name) entries.push(["Target name", `${selectors.target_name.matcher}: ${selectors.target_name.value}`]);
  if (selectors.uri) entries.push(["URI", `${selectors.uri.matcher}: ${selectors.uri.value}`]);
  Object.entries(selectors.labels ?? {}).forEach(([key, value]) => entries.push([`Label: ${key}`, value]));
  return entries;
};

const predicateValue = (predicate: PolicyPredicate): string =>
  "values" in predicate ? displayJson(predicate.values) : displayJson(predicate.value);

const RuleDetails = ({ rule }: { rule: ActionPolicyRule }) => (
  <div className="policy-rule">
    <div className="policy-rule__tags">
      <Tag type={rule.effect === "deny" ? "red" : "green"}>{rule.effect}</Tag>
      {rule.reason_code && <Tag type="gray">{rule.reason_code}</Tag>}
    </div>
    {rule.description && <p>{rule.description}</p>}
    {rule.message && <p><strong>Caller message:</strong> {rule.message}</p>}

    <h4>Selectors</h4>
    <StructuredListWrapper aria-label={`Selectors for ${rule.id}`}>
      <StructuredListBody>
        {selectorEntries(rule).map(([name, value]) => (
          <StructuredListRow key={name}>
            <StructuredListCell>{name}</StructuredListCell>
            <StructuredListCell>{value}</StructuredListCell>
          </StructuredListRow>
        ))}
      </StructuredListBody>
    </StructuredListWrapper>

    <h4>Predicates</h4>
    {(rule.predicates ?? []).length === 0 ? <p>None</p> : (
      <StructuredListWrapper aria-label={`Predicates for ${rule.id}`}>
        <StructuredListBody>
          {(rule.predicates ?? []).map((predicate, index) => (
            <StructuredListRow key={`${predicate.pointer}-${index}`}>
              <StructuredListCell>{predicate.pointer}</StructuredListCell>
              <StructuredListCell>{predicate.operator}</StructuredListCell>
              <StructuredListCell>{predicateValue(predicate)}</StructuredListCell>
            </StructuredListRow>
          ))}
        </StructuredListBody>
      </StructuredListWrapper>
    )}

    <h4>Metadata</h4>
    {Object.keys(rule.metadata ?? {}).length === 0 ? <p>None</p> : (
      <pre className="policy-json">{JSON.stringify(rule.metadata, null, 2)}</pre>
    )}
  </div>
);

export const PolicyDetails = ({ policy }: PolicyDetailsProps) => {
  const rules = policy.rules ?? [];
  if (rules.length === 0) return <p className="policy-empty">This policy has no rules.</p>;

  return (
    <Accordion align="start">
      {rules.map((rule) => (
        <AccordionItem key={rule.id} title={`${rule.id} — ${rule.effect}`}>
          <RuleDetails rule={rule} />
        </AccordionItem>
      ))}
    </Accordion>
  );
};

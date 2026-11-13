import type {
  ActionPolicy,
  ActionPolicyRule,
  PolicyPredicate,
} from '../../hooks/api/use-action-policies';

export type PredicateOperator = PolicyPredicate['operator'];
export type MatchKind = 'exact' | 'glob' | 'prefix';
export type TargetType = 'tool' | 'resource' | 'prompt' | 'agent';

export const PREDICATE_OPERATORS: PredicateOperator[] = [
  'exists',
  'equals',
  'not_equals',
  'one_of',
  'not_one_of',
];
export const TARGET_TYPES: TargetType[] = ['tool', 'resource', 'prompt', 'agent'];
/** The server accepts only these matchers for `target_name`. */
export const TARGET_NAME_MATCHERS: MatchKind[] = ['exact', 'glob'];
/** The server accepts only these matchers for `uri`. */
export const URI_MATCHERS: MatchKind[] = ['exact', 'prefix'];

/** A predicate in the editor. `value` is JSON text. */
export interface DraftPredicate {
  operator: PredicateOperator;
  pointer: string;
  value: string;
}

/** A rule in the editor. Every field is text, so the editor can keep input that is not valid yet. */
export interface DraftRule {
  key: number;
  id: string;
  description: string;
  effect: 'allow' | 'deny';
  reasonCode: string;
  message: string;
  namespace: string;
  operation: string;
  targetType: TargetType | '';
  targetNameMatcher: MatchKind;
  targetName: string;
  uriMatcher: MatchKind;
  uri: string;
  /** A JSON object of string values, or an empty string. */
  labels: string;
  /** A JSON object, or an empty string. */
  metadata: string;
  predicates: DraftPredicate[];
}

/** A problem in a draft rule. `field` names the invalid field, for example `predicates.0.pointer`. */
export interface DraftIssue {
  rule: number;
  field: string;
  message: string;
}

let nextKey = 0;

export const emptyRule = (): DraftRule => ({
  key: nextKey++,
  id: '',
  description: '',
  effect: 'deny',
  reasonCode: '',
  message: '',
  namespace: '',
  operation: '',
  targetType: '',
  targetNameMatcher: 'exact',
  targetName: '',
  uriMatcher: 'exact',
  uri: '',
  labels: '',
  metadata: '',
  predicates: [],
});

export const emptyPredicate = (): DraftPredicate => ({
  operator: 'equals',
  pointer: '',
  value: '',
});

const jsonText = (value: object | undefined): string =>
  value === undefined || Object.keys(value).length === 0 ? '' : JSON.stringify(value, null, 2);

const predicateToDraft = (predicate: PolicyPredicate): DraftPredicate => ({
  operator: predicate.operator,
  pointer: predicate.pointer,
  value: JSON.stringify('values' in predicate ? predicate.values : predicate.value),
});

const ruleToDraft = (rule: ActionPolicyRule): DraftRule => ({
  key: nextKey++,
  id: rule.id ?? '',
  description: rule.description ?? '',
  effect: rule.effect,
  reasonCode: rule.reason_code ?? '',
  message: rule.message ?? '',
  namespace: rule.selectors?.namespace ?? '',
  operation: rule.selectors?.operation ?? '',
  targetType: rule.selectors?.target_type ?? '',
  targetNameMatcher: rule.selectors?.target_name?.matcher ?? 'exact',
  targetName: rule.selectors?.target_name?.value ?? '',
  uriMatcher: rule.selectors?.uri?.matcher ?? 'exact',
  uri: rule.selectors?.uri?.value ?? '',
  labels: jsonText(rule.selectors?.labels),
  metadata: jsonText(rule.metadata),
  predicates: (rule.predicates ?? []).map(predicateToDraft),
});

export const policyToDraft = (policy: ActionPolicy): DraftRule[] =>
  (policy.rules ?? []).map(ruleToDraft);

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9\-_./:]*$/;
const IDENTIFIER_HINT =
  'Start with a letter or digit. Use only letters, digits, and the characters - _ . / :';

/** Checks the syntax of an RFC 6901 JSON Pointer. */
export const isValidJsonPointer = (pointer: string): boolean =>
  pointer === '' || (pointer.startsWith('/') && !/~(?![01])/.test(pointer));

const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

type Parsed = { value: Record<string, unknown> } | { error: string };

const parseObject = (text: string): Parsed => {
  if (text.trim() === '') return { value: {} };
  try {
    const value: unknown = JSON.parse(text);
    return isObject(value) ? { value } : { error: 'Enter a JSON object.' };
  } catch {
    return { error: 'Enter valid JSON.' };
  }
};

const optional = (text: string): string | undefined =>
  text.trim() === '' ? undefined : text.trim();

/**
 * Builds the policy from the draft and checks it with the same rules as the server, so that
 * the editor shows the problems before it sends the policy. The server stays the authority.
 */
export const buildPolicy = (draft: DraftRule[]): { policy: ActionPolicy; issues: DraftIssue[] } => {
  const issues: DraftIssue[] = [];
  const ids = new Set<string>();

  const rules = draft.map((draftRule, rule): ActionPolicyRule => {
    const report = (field: string, message: string) => issues.push({ rule, field, message });
    const identifier = (field: string, value: string | undefined, label: string) => {
      if (value !== undefined && !IDENTIFIER.test(value)) {
        report(field, `${label} is not valid. ${IDENTIFIER_HINT}.`);
      }
    };

    const id = draftRule.id.trim();
    if (id === '') report('id', 'Enter a rule ID.');
    else if (!IDENTIFIER.test(id)) report('id', `The rule ID is not valid. ${IDENTIFIER_HINT}.`);
    else if (ids.has(id)) report('id', `The rule ID "${id}" is used by more than one rule.`);
    ids.add(id);

    const namespace = optional(draftRule.namespace);
    const operation = optional(draftRule.operation);
    const reasonCode = optional(draftRule.reasonCode);
    identifier('namespace', namespace, 'The namespace');
    identifier('operation', operation, 'The operation');
    identifier('reason_code', reasonCode, 'The reason code');

    const targetName = optional(draftRule.targetName);
    const uri = optional(draftRule.uri);
    const targetNameMatcher = targetName
      ? { matcher: draftRule.targetNameMatcher, value: targetName }
      : undefined;
    const uriMatcher = uri ? { matcher: draftRule.uriMatcher, value: uri } : undefined;
    if (targetNameMatcher && !TARGET_NAME_MATCHERS.includes(targetNameMatcher.matcher)) {
      report('target_name', 'The target name matcher must be exact or glob.');
    }
    if (uriMatcher && !URI_MATCHERS.includes(uriMatcher.matcher)) {
      report('uri', 'The URI matcher must be exact or prefix.');
    }

    const labels = parseObject(draftRule.labels);
    let labelValues: Record<string, string> = {};
    if ('error' in labels) report('labels', labels.error);
    else {
      const entries = Object.entries(labels.value);
      if (entries.some(([, value]) => typeof value !== 'string')) {
        report('labels', 'Every label value must be a string.');
      } else {
        labelValues = Object.fromEntries(entries as Array<[string, string]>);
        if (Object.keys(labelValues).some((key) => !IDENTIFIER.test(key))) {
          report('labels', `A label name is not valid. ${IDENTIFIER_HINT}.`);
        }
      }
    }

    const metadata = parseObject(draftRule.metadata);
    let metadataValues: Record<string, unknown> = {};
    if ('error' in metadata) report('metadata', metadata.error);
    else {
      metadataValues = metadata.value;
      if (Object.keys(metadataValues).some((key) => !IDENTIFIER.test(key))) {
        report('metadata', `A metadata key is not valid. ${IDENTIFIER_HINT}.`);
      }
    }

    if (
      !namespace &&
      !operation &&
      !draftRule.targetType &&
      !targetName &&
      !uri &&
      Object.keys(labelValues).length === 0
    ) {
      report('selectors', 'Select at least one of namespace, operation, target, URI, or label.');
    }

    const predicates: PolicyPredicate[] = [];
    draftRule.predicates.forEach((predicate, index) => {
      const pointerField = `predicates.${index}.pointer`;
      const valueField = `predicates.${index}.value`;
      const pointer = predicate.pointer.trim();
      if (pointer === '')
        report(pointerField, 'Enter a JSON Pointer, for example /arguments/name.');
      else if (!isValidJsonPointer(pointer)) {
        report(
          pointerField,
          'A JSON Pointer must start with "/". Write "~" as "~0" and "/" as "~1".',
        );
      }

      let value: unknown;
      try {
        value = JSON.parse(predicate.value);
      } catch {
        report(valueField, 'Enter valid JSON, for example "production".');
        return;
      }
      if (predicate.operator === 'exists') {
        if (typeof value !== 'boolean') return report(valueField, 'Enter true or false.');
        predicates.push({ operator: 'exists', pointer, value });
      } else if (predicate.operator === 'equals' || predicate.operator === 'not_equals') {
        predicates.push({ operator: predicate.operator, pointer, value });
      } else {
        if (!Array.isArray(value) || value.length === 0) {
          return report(valueField, 'Enter a JSON array with at least one value.');
        }
        predicates.push({ operator: predicate.operator, pointer, values: value });
      }
    });

    return {
      id,
      effect: draftRule.effect,
      description: optional(draftRule.description) ?? null,
      reason_code: reasonCode ?? null,
      message: optional(draftRule.message) ?? null,
      selectors: {
        namespace: namespace ?? null,
        operation: operation ?? null,
        target_type: draftRule.targetType || null,
        target_name: targetNameMatcher ?? null,
        uri: uriMatcher ?? null,
        labels: labelValues,
      },
      predicates,
      metadata: metadataValues,
    };
  });

  return { policy: { rules }, issues };
};

/** Reads policy JSON that an operator pasted. It accepts a policy or a `{ "policy": ... }` wrapper. */
export const parsePolicyJson = (text: string): { policy: ActionPolicy } | { error: string } => {
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch (error) {
    return {
      error: `The text is not valid JSON. ${error instanceof Error ? error.message : ''}`.trim(),
    };
  }
  const root =
    isObject(value) && !('rules' in value) && isObject(value.policy) ? value.policy : value;
  if (!isObject(root) || !Array.isArray(root.rules)) {
    return { error: 'The policy must be a JSON object with a "rules" array.' };
  }
  for (const [index, rule] of root.rules.entries()) {
    if (!isObject(rule)) return { error: `Rule ${index + 1} must be a JSON object.` };
    if (rule.effect !== 'allow' && rule.effect !== 'deny') {
      return { error: `Rule ${index + 1} must have the effect "allow" or "deny".` };
    }
  }
  return { policy: root as ActionPolicy };
};

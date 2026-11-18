import type { EvaluatorDef } from '../models';

export interface FieldChange {
  /** The dotted path of the field, for example `engine.prompt`. */
  path: string;
  /** The value in the base configuration, or `undefined` when the field is new. */
  before?: unknown;
  /** The value in the compared configuration, or `undefined` when the field is removed. */
  after?: unknown;
}

export interface EvaluatorChange {
  name: string;
  kind: 'added' | 'removed' | 'changed';
  /** The changed fields. Empty for an added or removed evaluator. */
  fields: FieldChange[];
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const diffValues = (path: string, before: unknown, after: unknown, out: FieldChange[]) => {
  if (isRecord(before) && isRecord(after)) {
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])].sort();
    keys.forEach((key) => diffValues(path ? `${path}.${key}` : key, before[key], after[key], out));
    return;
  }
  if (JSON.stringify(before) !== JSON.stringify(after)) out.push({ path, before, after });
};

/**
 * Lists how `compared` differs from `base`, matching evaluators by name. The result is sorted by
 * evaluator name. Evaluators without any difference are omitted.
 */
export const diffEvaluators = (
  base: EvaluatorDef[],
  compared: EvaluatorDef[],
): EvaluatorChange[] => {
  const baseByName = new Map(base.map((evaluator) => [evaluator.name, evaluator]));
  const comparedByName = new Map(compared.map((evaluator) => [evaluator.name, evaluator]));
  const names = [...new Set([...baseByName.keys(), ...comparedByName.keys()])].sort();

  return names.flatMap((name): EvaluatorChange[] => {
    const before = baseByName.get(name);
    const after = comparedByName.get(name);
    if (!before) return [{ name, kind: 'added', fields: [] }];
    if (!after) return [{ name, kind: 'removed', fields: [] }];
    const fields: FieldChange[] = [];
    diffValues('', before, after, fields);
    return fields.length === 0 ? [] : [{ name, kind: 'changed', fields }];
  });
};

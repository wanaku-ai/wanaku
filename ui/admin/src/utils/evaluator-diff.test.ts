import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { EvaluatorDef } from '../models';
import { diffEvaluators } from './evaluator-diff.ts';

const evaluator = (name: string, overrides: Partial<EvaluatorDef> = {}): EvaluatorDef => ({
  name,
  trigger: { method: 'tools/call' },
  engine: { type: 'passthrough' },
  processor: { path: 'a.wasm' },
  ...overrides,
});

describe('diffEvaluators', () => {
  it('returns nothing for identical configurations', () => {
    assert.deepEqual(diffEvaluators([evaluator('a')], [evaluator('a')]), []);
  });

  it('reports added and removed evaluators by name', () => {
    assert.deepEqual(diffEvaluators([evaluator('old')], [evaluator('new')]), [
      { name: 'new', kind: 'added', fields: [] },
      { name: 'old', kind: 'removed', fields: [] },
    ]);
  });

  it('reports changed fields with their paths', () => {
    const changes = diffEvaluators(
      [evaluator('a')],
      [
        evaluator('a', {
          trigger: { method: 'tools/call', namespace: 'prod' },
          engine: { type: 'opa', connection: 'policy', decision_path: 'wanaku/allow' },
          processor: { path: 'b.wasm' },
        }),
      ],
    );
    assert.deepEqual(changes, [
      {
        name: 'a',
        kind: 'changed',
        fields: [
          { path: 'engine.connection', before: undefined, after: 'policy' },
          { path: 'engine.decision_path', before: undefined, after: 'wanaku/allow' },
          { path: 'engine.type', before: 'passthrough', after: 'opa' },
          { path: 'processor.path', before: 'a.wasm', after: 'b.wasm' },
          { path: 'trigger.namespace', before: undefined, after: 'prod' },
        ],
      },
    ]);
  });

  it('compares nested objects by value, not by key order', () => {
    const llm = (schema: unknown): EvaluatorDef =>
      evaluator('a', {
        engine: {
          type: 'llm',
          operation: 'classify',
          prompt: 'p',
          connection: 'c',
          result_schema: schema,
        },
      });
    assert.deepEqual(diffEvaluators([llm({ a: 1, b: 2 })], [llm({ b: 2, a: 1 })]), []);
  });
});

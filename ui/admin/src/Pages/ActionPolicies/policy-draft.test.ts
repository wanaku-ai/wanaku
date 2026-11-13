import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildPolicy,
  emptyPredicate,
  emptyRule,
  isValidJsonPointer,
  parsePolicyJson,
  policyToDraft,
} from './policy-draft.ts';

const policy = {
  rules: [
    {
      id: 'protect-production',
      description: 'Protect production services',
      effect: 'deny' as const,
      selectors: {
        operation: 'tools/call',
        target_type: 'tool' as const,
        target_name: { matcher: 'glob' as const, value: 'restart*' },
        labels: { tier: 'prod' },
      },
      predicates: [
        { operator: 'equals' as const, pointer: '/arguments/service', value: 'production' },
        { operator: 'one_of' as const, pointer: '/arguments/region', values: ['eu', 'us'] },
        { operator: 'exists' as const, pointer: '/arguments/force', value: true },
      ],
      reason_code: 'production_restart_denied',
      message: 'Production restarts are not permitted.',
      metadata: { owner: 'platform' },
    },
  ],
};

const validRule = () => ({ ...emptyRule(), id: 'rule-1', operation: 'tools/call' });
const fields = (draft: ReturnType<typeof validRule>[]) =>
  buildPolicy(draft).issues.map(({ rule, field }) => `${rule}:${field}`);

describe('isValidJsonPointer', () => {
  it('accepts RFC 6901 pointers', () => {
    for (const pointer of ['', '/', '/a/b', '/a~0b/c~1d', '/arguments/0']) {
      assert.equal(isValidJsonPointer(pointer), true, pointer);
    }
  });

  it('rejects pointers without a leading slash and bad escapes', () => {
    for (const pointer of ['a', 'arguments/name', '/a~', '/a~2', '/a~b']) {
      assert.equal(isValidJsonPointer(pointer), false, pointer);
    }
  });
});

describe('policyToDraft and buildPolicy', () => {
  it('round-trips a policy without loss', () => {
    const result = buildPolicy(policyToDraft(policy));
    assert.deepEqual(result.issues, []);
    const [rule] = result.policy.rules ?? [];
    assert.equal(rule?.id, 'protect-production');
    assert.deepEqual(rule?.selectors?.target_name, { matcher: 'glob', value: 'restart*' });
    assert.deepEqual(rule?.selectors?.labels, { tier: 'prod' });
    assert.deepEqual(rule?.predicates, policy.rules[0]?.predicates);
    assert.deepEqual(rule?.metadata, { owner: 'platform' });
    assert.equal(rule?.message, 'Production restarts are not permitted.');
  });

  it('builds a valid rule from an empty draft with one selector', () => {
    assert.deepEqual(buildPolicy([validRule()]).issues, []);
  });
});

describe('buildPolicy validation', () => {
  it('requires a rule ID and a selector', () => {
    assert.deepEqual(fields([emptyRule()]), ['0:id', '0:selectors']);
  });

  it('rejects duplicate and malformed identifiers', () => {
    const duplicate = { ...validRule(), id: 'a' };
    assert.deepEqual(fields([duplicate, { ...duplicate }]), ['1:id']);
    assert.deepEqual(fields([{ ...validRule(), id: '-a' }]), ['0:id']);
    assert.deepEqual(fields([{ ...validRule(), reasonCode: 'has space' }]), ['0:reason_code']);
    assert.deepEqual(fields([{ ...validRule(), namespace: 'a b' }]), ['0:namespace']);
  });

  it('rejects matchers that the server does not accept', () => {
    const name = { ...validRule(), targetName: 'x', targetNameMatcher: 'prefix' as const };
    const uri = { ...validRule(), id: 'rule-2', uri: 'x', uriMatcher: 'glob' as const };
    assert.deepEqual(fields([name, uri]), ['0:target_name', '1:uri']);
  });

  it('rejects invalid labels and metadata', () => {
    assert.deepEqual(fields([{ ...validRule(), labels: '{' }]), ['0:labels']);
    assert.deepEqual(fields([{ ...validRule(), labels: '{"a": 1}' }]), ['0:labels']);
    assert.deepEqual(fields([{ ...validRule(), metadata: '[]' }]), ['0:metadata']);
    assert.deepEqual(fields([{ ...validRule(), metadata: '{"bad key": 1}' }]), ['0:metadata']);
  });

  it('validates predicate pointers and values', () => {
    const rule = (operator: 'exists' | 'equals' | 'one_of', pointer: string, value: string) => ({
      ...validRule(),
      predicates: [{ operator, pointer, value }],
    });
    assert.deepEqual(fields([rule('equals', 'arguments/name', '"x"')]), ['0:predicates.0.pointer']);
    assert.deepEqual(fields([rule('equals', '', '"x"')]), ['0:predicates.0.pointer']);
    assert.deepEqual(fields([rule('equals', '/a', 'production')]), ['0:predicates.0.value']);
    assert.deepEqual(fields([rule('exists', '/a', '"yes"')]), ['0:predicates.0.value']);
    assert.deepEqual(fields([rule('one_of', '/a', '[]')]), ['0:predicates.0.value']);
    assert.deepEqual(fields([rule('one_of', '/a', '"x"')]), ['0:predicates.0.value']);
    assert.deepEqual(fields([rule('one_of', '/a', '["x"]')]), []);
  });

  it('starts a new predicate with the equals operator', () => {
    assert.equal(emptyPredicate().operator, 'equals');
  });
});

describe('parsePolicyJson', () => {
  it('accepts a policy and a wrapped policy', () => {
    assert.deepEqual(parsePolicyJson(JSON.stringify(policy)), { policy });
    assert.deepEqual(parsePolicyJson(JSON.stringify({ policy, expected_revision: 2 })), {
      policy,
    });
  });

  it('describes input that is not a policy', () => {
    for (const text of ['{', '[]', '{}', '{"rules": [1]}', '{"rules": [{"id": "a"}]}']) {
      assert.ok('error' in parsePolicyJson(text), text);
    }
  });
});

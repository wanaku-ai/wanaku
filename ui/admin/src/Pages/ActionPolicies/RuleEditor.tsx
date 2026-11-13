import { Button, FormGroup, Select, SelectItem, Stack, TextArea, TextInput } from '@carbon/react';
import { Add, TrashCan } from '@carbon/icons-react';
import {
  PREDICATE_OPERATORS,
  TARGET_NAME_MATCHERS,
  TARGET_TYPES,
  URI_MATCHERS,
  emptyPredicate,
  type DraftPredicate,
  type DraftRule,
  type MatchKind,
  type PredicateOperator,
  type TargetType,
} from './policy-draft';

interface RuleEditorProps {
  rule: DraftRule;
  /** Maps a field name, for example `predicates.0.pointer`, to its validation message. */
  errors: Map<string, string>;
  onChange: (rule: DraftRule) => void;
}

const VALUE_HELP: Record<PredicateOperator, string> = {
  exists: 'Enter true or false.',
  equals: 'Enter a JSON value, for example "production" or 5.',
  not_equals: 'Enter a JSON value, for example "production" or 5.',
  one_of: 'Enter a JSON array, for example ["eu", "us"].',
  not_one_of: 'Enter a JSON array, for example ["eu", "us"].',
};

export const RuleEditor = ({ rule, errors, onChange }: RuleEditorProps) => {
  const id = (field: string) => `rule-${rule.key}-${field.replace(/\./g, '-')}`;
  const set = <K extends keyof DraftRule>(field: K, value: DraftRule[K]) =>
    onChange({ ...rule, [field]: value });
  const invalid = (field: string) => ({
    invalid: errors.has(field),
    invalidText: errors.get(field),
  });

  const setPredicate = (index: number, change: Partial<DraftPredicate>) =>
    set(
      'predicates',
      rule.predicates.map((predicate, i) =>
        i === index ? { ...predicate, ...change } : predicate,
      ),
    );

  return (
    <Stack gap={6}>
      <TextInput
        id={id('id')}
        labelText="Rule ID"
        helperText="A unique name for the rule. Start with a letter or digit. Use letters, digits, and - _ . / :"
        value={rule.id}
        onChange={(event) => set('id', event.target.value)}
        {...invalid('id')}
      />
      <Select
        id={id('effect')}
        labelText="Effect"
        value={rule.effect}
        onChange={(event) => set('effect', event.target.value as DraftRule['effect'])}
      >
        <SelectItem value="deny" text="Deny" />
        <SelectItem value="allow" text="Allow" />
      </Select>
      <TextInput
        id={id('description')}
        labelText="Description (optional)"
        value={rule.description}
        onChange={(event) => set('description', event.target.value)}
      />
      <TextInput
        id={id('reason_code')}
        labelText="Reason code (optional)"
        helperText="A short code for the audit trail, for example production_restart_denied."
        value={rule.reasonCode}
        onChange={(event) => set('reasonCode', event.target.value)}
        {...invalid('reason_code')}
      />
      <TextInput
        id={id('message')}
        labelText="Message for the caller (optional)"
        helperText="The caller sees this message when the rule denies an action. Do not include secrets."
        value={rule.message}
        onChange={(event) => set('message', event.target.value)}
      />

      <FormGroup
        legendText="Selectors"
        message={errors.has('selectors')}
        messageText={errors.get('selectors')}
        invalid={errors.has('selectors')}
      >
        <Stack gap={5}>
          <TextInput
            id={id('namespace')}
            labelText="Namespace"
            value={rule.namespace}
            onChange={(event) => set('namespace', event.target.value)}
            {...invalid('namespace')}
          />
          <TextInput
            id={id('operation')}
            labelText="Operation"
            helperText="For example tools/call."
            value={rule.operation}
            onChange={(event) => set('operation', event.target.value)}
            {...invalid('operation')}
          />
          <Select
            id={id('target_type')}
            labelText="Target type"
            value={rule.targetType}
            onChange={(event) => set('targetType', event.target.value as TargetType | '')}
          >
            <SelectItem value="" text="Any" />
            {TARGET_TYPES.map((type) => (
              <SelectItem key={type} value={type} text={type} />
            ))}
          </Select>
          <MatchField
            id={id('target_name')}
            label="Target name"
            matchers={TARGET_NAME_MATCHERS}
            matcher={rule.targetNameMatcher}
            value={rule.targetName}
            error={errors.get('target_name')}
            onMatcher={(matcher) => set('targetNameMatcher', matcher)}
            onValue={(value) => set('targetName', value)}
          />
          <MatchField
            id={id('uri')}
            label="URI"
            matchers={URI_MATCHERS}
            matcher={rule.uriMatcher}
            value={rule.uri}
            error={errors.get('uri')}
            onMatcher={(matcher) => set('uriMatcher', matcher)}
            onValue={(value) => set('uri', value)}
          />
          <TextArea
            id={id('labels')}
            labelText="Labels (JSON object)"
            helperText='For example {"tier": "prod"}. Every value is a string.'
            rows={3}
            value={rule.labels}
            onChange={(event) => set('labels', event.target.value)}
            {...invalid('labels')}
          />
        </Stack>
      </FormGroup>

      <FormGroup legendText="Predicates">
        <Stack gap={5}>
          {rule.predicates.map((predicate, index) => (
            <div key={index} className="policy-editor__predicate">
              <Select
                id={id(`predicates.${index}.operator`)}
                labelText="Operator"
                value={predicate.operator}
                onChange={(event) =>
                  setPredicate(index, { operator: event.target.value as PredicateOperator })
                }
              >
                {PREDICATE_OPERATORS.map((operator) => (
                  <SelectItem key={operator} value={operator} text={operator} />
                ))}
              </Select>
              <TextInput
                id={id(`predicates.${index}.pointer`)}
                labelText="JSON Pointer"
                placeholder="/arguments/service"
                value={predicate.pointer}
                onChange={(event) => setPredicate(index, { pointer: event.target.value })}
                {...invalid(`predicates.${index}.pointer`)}
              />
              <TextInput
                id={id(`predicates.${index}.value`)}
                labelText="Value (JSON)"
                helperText={VALUE_HELP[predicate.operator]}
                value={predicate.value}
                onChange={(event) => setPredicate(index, { value: event.target.value })}
                {...invalid(`predicates.${index}.value`)}
              />
              <Button
                kind="ghost"
                size="md"
                hasIconOnly
                renderIcon={TrashCan}
                iconDescription={`Remove predicate ${index + 1}`}
                onClick={() =>
                  set(
                    'predicates',
                    rule.predicates.filter((_, i) => i !== index),
                  )
                }
              />
            </div>
          ))}
          <div>
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              onClick={() => set('predicates', [...rule.predicates, emptyPredicate()])}
            >
              Add predicate
            </Button>
          </div>
        </Stack>
      </FormGroup>

      <TextArea
        id={id('metadata')}
        labelText="Metadata (JSON object, optional)"
        helperText='For example {"owner": "platform"}.'
        rows={3}
        value={rule.metadata}
        onChange={(event) => set('metadata', event.target.value)}
        {...invalid('metadata')}
      />
    </Stack>
  );
};

interface MatchFieldProps {
  id: string;
  label: string;
  matchers: MatchKind[];
  matcher: MatchKind;
  value: string;
  error?: string;
  onMatcher: (matcher: MatchKind) => void;
  onValue: (value: string) => void;
}

const MatchField = ({
  id,
  label,
  matchers,
  matcher,
  value,
  error,
  onMatcher,
  onValue,
}: MatchFieldProps) => (
  <div className="policy-editor__match">
    <Select
      id={`${id}-matcher`}
      labelText={`${label} matcher`}
      value={matcher}
      onChange={(event) => onMatcher(event.target.value as MatchKind)}
    >
      {matchers.map((kind) => (
        <SelectItem key={kind} value={kind} text={kind} />
      ))}
    </Select>
    <TextInput
      id={`${id}-value`}
      labelText={label}
      value={value}
      onChange={(event) => onValue(event.target.value)}
      invalid={error !== undefined}
      invalidText={error}
    />
  </div>
);

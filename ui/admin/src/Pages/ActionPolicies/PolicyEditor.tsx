import {
  Accordion,
  AccordionItem,
  ActionableNotification,
  Button,
  InlineNotification,
  Modal,
  Stack,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  TextArea,
} from '@carbon/react';
import { Add, TrashCan } from '@carbon/icons-react';
import { useState } from 'react';
import {
  actionPolicyErrorKind,
  type ActionPolicy,
  type ActionPolicyRevision,
} from '../../hooks/api/use-action-policies';
import { RuleEditor } from './RuleEditor';
import {
  buildPolicy,
  emptyRule,
  parsePolicyJson,
  policyToDraft,
  type DraftIssue,
  type DraftRule,
} from './policy-draft';

interface PolicyEditorProps {
  /** The active revision that the draft starts from, or `undefined` when no policy is active. */
  base?: ActionPolicyRevision;
  /** Sends the policy to the server with the base revision as the expected revision. */
  onActivate: (policy: ActionPolicy) => Promise<void>;
  /** Discards the draft and loads the active revision again. */
  onReload: () => void;
  onCancel: () => void;
}

type Failure = { kind: 'conflict' | 'validation' | 'other'; message: string };

const JSON_TAB = 1;

const issueText = (issue: DraftIssue, rules: DraftRule[]): string => {
  const id = rules[issue.rule]?.id.trim();
  return `Rule ${issue.rule + 1}${id ? ` (${id})` : ''}: ${issue.message}`;
};

const toJson = (policy: ActionPolicy): string => JSON.stringify(policy, null, 2);

export const PolicyEditor = ({ base, onActivate, onReload, onCancel }: PolicyEditorProps) => {
  const [rules, setRules] = useState<DraftRule[]>(() => policyToDraft(base?.policy ?? {}));
  const [json, setJson] = useState('');
  const [tab, setTab] = useState(0);
  const [jsonError, setJsonError] = useState<string>();
  const [issues, setIssues] = useState<DraftIssue[]>([]);
  const [jsonIssues, setJsonIssues] = useState<string[]>([]);
  const [pending, setPending] = useState<ActionPolicy>();
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<Failure>();
  const [addedKey, setAddedKey] = useState<number>();

  const selectTab = (index: number) => {
    if (index === JSON_TAB) {
      setJson(toJson(buildPolicy(rules).policy));
      setJsonError(undefined);
      setTab(index);
      return;
    }
    const parsed = parsePolicyJson(json);
    if ('error' in parsed) {
      setJsonError(parsed.error);
      return;
    }
    setRules(policyToDraft(parsed.policy));
    setIssues([]);
    setJsonIssues([]);
    setJsonError(undefined);
    setTab(index);
  };

  const addRule = () => {
    const rule = emptyRule();
    setAddedKey(rule.key);
    setRules([...rules, rule]);
  };

  /** Validates the draft. Returns the policy to send, or `undefined` when it has problems. */
  const validate = (): ActionPolicy | undefined => {
    setFailure(undefined);
    if (tab === JSON_TAB) {
      const parsed = parsePolicyJson(json);
      if ('error' in parsed) {
        setJsonError(parsed.error);
        return undefined;
      }
      setJsonError(undefined);
      const found = buildPolicy(policyToDraft(parsed.policy));
      const messages = found.issues.map((issue) => issueText(issue, policyToDraft(parsed.policy)));
      setJsonIssues(messages);
      return messages.length === 0 ? parsed.policy : undefined;
    }
    const built = buildPolicy(rules);
    setIssues(built.issues);
    return built.issues.length === 0 ? built.policy : undefined;
  };

  const confirm = async () => {
    if (!pending) return;
    setSaving(true);
    try {
      await onActivate(pending);
    } catch (error) {
      const kind = actionPolicyErrorKind(error);
      setFailure({
        kind: kind === 'conflict' || kind === 'validation' ? kind : 'other',
        message: error instanceof Error ? error.message : 'The policy could not be activated.',
      });
    } finally {
      setSaving(false);
      setPending(undefined);
    }
  };

  const issuesFor = (index: number) =>
    new Map(issues.filter((issue) => issue.rule === index).map((i) => [i.field, i.message]));
  const allMessages = tab === JSON_TAB ? jsonIssues : issues.map((i) => issueText(i, rules));

  return (
    <div className="policy-editor">
      {failure?.kind === 'conflict' && (
        <ActionableNotification
          kind="error"
          title="Another change was activated first"
          subtitle={`${failure.message}. Your draft is based on an older revision. Reload the active revision to continue. Reloading discards this draft, so copy it from the JSON tab first if you want to keep it.`}
          lowContrast
          hideCloseButton
          inline
          actionButtonLabel="Reload active revision"
          onActionButtonClick={onReload}
        />
      )}
      {failure?.kind === 'validation' && (
        <InlineNotification
          kind="error"
          title="The server rejected the policy"
          subtitle={`${failure.message}. Your draft is unchanged. Fix the problem and activate again.`}
          lowContrast
          hideCloseButton
        />
      )}
      {failure?.kind === 'other' && (
        <InlineNotification
          kind="error"
          title="The policy was not activated"
          subtitle={failure.message}
          lowContrast
          hideCloseButton
        />
      )}
      {allMessages.length > 0 && (
        <InlineNotification
          kind="error"
          title="Fix these problems before you activate the policy"
          subtitle=""
          lowContrast
          hideCloseButton
        >
          <ul className="policy-editor__issues">
            {allMessages.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </InlineNotification>
      )}

      <Tabs selectedIndex={tab} onChange={({ selectedIndex }) => selectTab(selectedIndex)}>
        <TabList aria-label="Policy editor">
          <Tab>Structured editor</Tab>
          <Tab>JSON editor</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
            <Stack gap={5}>
              {rules.length === 0 ? (
                <p className="policy-empty">This policy has no rules. Add a rule to start.</p>
              ) : (
                <Accordion align="start">
                  {rules.map((rule, index) => (
                    <AccordionItem
                      key={rule.key}
                      title={`${rule.id.trim() || 'New rule'} — ${rule.effect}`}
                      open={rule.key === addedKey || issuesFor(index).size > 0}
                    >
                      <RuleEditor
                        rule={rule}
                        errors={issuesFor(index)}
                        onChange={(changed) =>
                          setRules(rules.map((r, i) => (i === index ? changed : r)))
                        }
                      />
                      <Button
                        kind="danger--tertiary"
                        size="sm"
                        renderIcon={TrashCan}
                        className="policy-editor__remove"
                        onClick={() => setRules(rules.filter((_, i) => i !== index))}
                      >
                        Remove rule
                      </Button>
                    </AccordionItem>
                  ))}
                </Accordion>
              )}
              <div>
                <Button kind="tertiary" size="md" renderIcon={Add} onClick={addRule}>
                  Add rule
                </Button>
              </div>
            </Stack>
          </TabPanel>
          <TabPanel>
            <TextArea
              id="policy-json"
              labelText="Policy JSON"
              helperText='Paste a policy, for example {"rules": [...]}. Switch to the structured editor to apply it to the draft.'
              rows={24}
              value={json}
              onChange={(event) => setJson(event.target.value)}
              invalid={jsonError !== undefined}
              invalidText={jsonError}
            />
          </TabPanel>
        </TabPanels>
      </Tabs>

      <div className="policy-editor__actions">
        <Button
          onClick={() => {
            const policy = validate();
            if (policy) setPending(policy);
          }}
        >
          Activate policy
        </Button>
        <Button kind="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>

      <Modal
        open={pending !== undefined}
        danger
        modalHeading="Activate this policy?"
        primaryButtonText="Activate"
        secondaryButtonText="Cancel"
        primaryButtonDisabled={saving}
        onRequestSubmit={() => void confirm()}
        onRequestClose={() => setPending(undefined)}
      >
        <p>
          The policy has {pending?.rules?.length ?? 0} rule(s).{' '}
          {base
            ? `It replaces revision ${base.revision.id}.`
            : 'No policy is active now, so it becomes the first active policy.'}{' '}
          Wanaku enforces it for new requests immediately.
        </p>
      </Modal>
    </div>
  );
};

import {
  Button,
  InlineLoading,
  InlineNotification,
  Select,
  SelectItem,
  TextArea,
} from '@carbon/react';
import { useEffect, useState } from 'react';
import { parseDocument, stringify } from 'yaml';
import { ApiError } from '../../custom-fetch';
import { useSimulationTargets } from '../../hooks/api/use-simulation-targets';
import { useNamespaces } from '../../hooks/api/use-namespaces';
import { sortedNamespaces } from '../Namespaces/namespaces';
import { usePolicySimulation } from '../../hooks/api/use-policy-simulation';
import type {
  CandidateSelection,
  SyntheticAction,
  ValidationReport,
  SimulationReport,
  ReplayJob,
  NamespaceEntry,
  SimulationError,
} from '../../models';
import { AuditEventPicker } from './AuditEventPicker';
import { YamlEditor } from './YamlEditor';
import { ValidationResults, SimulationResults, ReplayResults } from './SimulationResults';
import './PolicySimulationPage.scss';

const candidateTemplate = `# Transient example: deny tool calls without changing active configuration.
action_policy:
  rules:
    - id: example-deny-tool-calls
      description: Block tool calls in this transient candidate.
      effect: deny
      selectors:
        operation: tools/call
        target_type: tool
      reason_code: example_tool_call_denied
# This example does not inherit active evaluators.
evaluators: []
`;

const parseObject = (value: string, label: string): Record<string, unknown> => {
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch {
    throw new Error(`${label} must contain valid JSON.`);
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed))
    throw new Error(`${label} must be a JSON object.`);
  return parsed as Record<string, unknown>;
};

const parseCandidate = (
  value: string,
  bases?: { policy: number; evaluator: number },
): CandidateSelection => {
  const document = parseDocument(value);
  if (document.errors.length > 0)
    throw new Error(`Candidate must contain valid YAML. ${document.errors[0].message}`);
  const parsed: unknown = document.toJS({ maxAliasCount: 50 });
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed))
    throw new Error('Candidate must be a YAML mapping.');
  const config = parsed as Record<string, unknown>;
  if (!('action_policy' in config) && !('evaluators' in config))
    return config as CandidateSelection;
  const unsupported = Object.keys(config).filter(
    (key) => key !== 'action_policy' && key !== 'evaluators',
  );
  if (unsupported.length)
    throw new Error(
      `Unsupported simulation configuration fields: ${unsupported.join(', ')}. Use action_policy and evaluators.`,
    );
  if (!bases) throw new Error('Current base revisions are still loading.');
  return {
    ...('action_policy' in config
      ? { base_policy_revision: bases.policy, inline_policy: config.action_policy }
      : {}),
    ...('evaluators' in config
      ? { base_evaluator_revision: bases.evaluator, inline_evaluators: config.evaluators }
      : {}),
  };
};

const createAction = (
  namespace: string,
  method: string,
  target: string,
  argumentsText: string,
): SyntheticAction => {
  if (!target.trim() || !namespace.trim()) throw new Error('Namespace and target are required.');
  const params =
    method === 'resources/read'
      ? { uri: target.trim() }
      : { name: target.trim(), arguments: parseObject(argumentsText, 'Arguments') };
  return { namespace: namespace.trim(), request: { jsonrpc: '2.0', id: 1, method, params } };
};

export const PolicySimulationPage = () => {
  const { validate, simulate, replay, getReplay, cancelReplay, deleteReplay, getBaseRevisions } =
    usePolicySimulation();
  const [candidateText, setCandidateText] = useState(candidateTemplate);
  const [bases, setBases] = useState<{ policy: number; evaluator: number }>();
  const { listNamespaces } = useNamespaces();
  const [namespaces, setNamespaces] = useState<NamespaceEntry[]>();
  const [namespaceError, setNamespaceError] = useState('');
  const [namespace, setNamespace] = useState('');
  const [method, setMethod] = useState('tools/call');
  const [target, setTarget] = useState('');
  const {
    targets,
    loading: targetsLoading,
    error: targetError,
  } = useSimulationTargets(namespace, method);
  const actionTarget =
    targets.find((entry) => entry.value === target)?.value ?? targets[0]?.value ?? '';
  const [argumentsText, setArgumentsText] = useState('{}');
  const [replayActions, setReplayActions] = useState<SyntheticAction[]>([]);
  const [auditIds, setAuditIds] = useState<string[]>([]);
  const [validation, setValidation] = useState<ValidationReport>();
  const [simulation, setSimulation] = useState<SimulationReport>();
  const [job, setJob] = useState<ReplayJob>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [polling, setPolling] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const response = await listNamespaces({ signal: controller.signal });
        if (response.status !== 200) throw new Error('Registered namespaces are unavailable.');
        const registered = sortedNamespaces(response.data);
        setNamespaces(registered);
        setNamespace(registered[0]?.name ?? '');
      } catch (cause) {
        if (!controller.signal.aborted) {
          setNamespaces([]);
          setNamespaceError(
            cause instanceof Error ? cause.message : 'Registered namespaces are unavailable.',
          );
        }
      }
    })();
    return () => controller.abort();
  }, [listNamespaces]);

  useEffect(() => {
    const controller = new AbortController();
    void getBaseRevisions(controller.signal)
      .then((loaded) => {
        if (!controller.signal.aborted) setBases(loaded);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setError(
            cause instanceof Error ? cause.message : 'Failed to load current base revisions.',
          );
      });
    return () => controller.abort();
  }, [getBaseRevisions]);

  const jobId = job?.id;
  const jobStatus = job?.status;
  useEffect(() => {
    if (!jobId || jobStatus !== 'running' || !polling || busy) return;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        setJob(await getReplay(jobId, controller.signal));
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : 'Replay refresh failed');
          setPolling(false);
        }
      }
    }, 1000);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [job, jobId, jobStatus, polling, busy, getReplay]);

  const run = async (operation: () => Promise<void>) => {
    setBusy(true);
    setError('');
    try {
      await operation();
    } catch (cause) {
      if (
        cause instanceof ApiError &&
        cause.status === 422 &&
        cause.data &&
        typeof cause.data === 'object'
      ) {
        const failure = cause.data as SimulationError;
        if (failure.validation) setValidation(failure.validation);
      }
      setError(
        cause instanceof Error && cause.message === 'active_configuration_invalid'
          ? 'The active configuration is invalid. Replay needs a valid active baseline. Review the validation report and correct the active configuration.'
          : cause instanceof Error
            ? cause.message
            : 'Policy test failed',
      );
    } finally {
      setBusy(false);
    }
  };
  const candidate = (): CandidateSelection => parseCandidate(candidateText, bases);
  const running = job?.status === 'running';

  return (
    <div>
      <h1 className="title">Policy Simulation</h1>
      <p className="description">
        Validate a candidate, simulate an MCP action, or compare actions with the active policy.
      </p>
      <div id="page-content" className="policy-simulation">
        <InlineNotification
          kind="info"
          title="Isolated policy testing"
          subtitle="Candidates remain transient. Tests do not activate policy, forward MCP requests, or resolve credentials. External evaluators are disabled."
          hideCloseButton
        />
        {error && (
          <div className="policy-simulation__error">
            <InlineNotification
              kind="error"
              title="Policy test failed"
              subtitle={error}
              hideCloseButton
            />
          </div>
        )}
        <section aria-label="Candidate configuration">
          <h2>Candidate configuration</h2>
          <p>
            {bases
              ? `Base policy revision: ${bases.policy} · Base evaluator revision: ${bases.evaluator}`
              : 'Loading current base revisions…'}
          </p>
          <YamlEditor
            id="simulation-candidate"
            labelText="Candidate configuration (YAML)"
            helperText="Use action_policy and evaluators exactly as in wanaku.yaml. Omitted sections use active configuration. Set evaluators: [] to test without active evaluators. Base revisions are supplied separately."
            value={candidateText}
            disabled={busy}
            onChange={(value) => {
              setCandidateText(value);
              setValidation(undefined);
              setSimulation(undefined);
            }}
          />
          <Button
            disabled={busy || !bases}
            onClick={() =>
              void run(async () => {
                setValidation(undefined);
                setValidation(await validate(candidate()));
              })
            }
          >
            Validate candidate
          </Button>
          <Button
            kind="secondary"
            disabled={busy || !actionTarget}
            onClick={() =>
              void run(async () => {
                const active = await validate({});
                const selectors = {
                  namespace,
                  operation: method,
                  ...(method === 'resources/read'
                    ? { uri: { matcher: 'exact', value: actionTarget } }
                    : { target_name: { matcher: 'exact', value: actionTarget } }),
                };
                setBases({
                  policy:
                    active.identity.base_policy_revision ?? active.identity.policy_revision ?? 0,
                  evaluator:
                    active.identity.base_evaluator_revision ??
                    active.identity.evaluator_revision ??
                    0,
                });
                const example = {
                  action_policy: {
                    rules: [
                      {
                        id: 'example-deny-selected-action',
                        effect: 'deny',
                        selectors,
                        reason_code: 'example_action_denied',
                      },
                    ],
                  },
                  evaluators: [],
                };
                setCandidateText(
                  '# Transient example: deny the selected action.\n# Evaluators are empty for this example only.\n' +
                    stringify(example),
                );
                setValidation(undefined);
                setSimulation(undefined);
              })
            }
          >
            Load example candidate
          </Button>
          <p>
            Select a registered action before loading an example. The example uses current base
            revisions and an empty evaluator list for this transient candidate.
          </p>
          {validation && <ValidationResults report={validation} />}
        </section>
        <section aria-label="Single-action simulation">
          <h2>Single-action simulation</h2>
          {namespaceError && (
            <InlineNotification
              kind="error"
              title="Namespaces are unavailable"
              subtitle={namespaceError}
              hideCloseButton
            />
          )}
          <Select
            id="simulation-namespace"
            labelText="Namespace"
            value={namespace}
            disabled={busy || !namespaces?.length}
            helperText={
              namespaces === undefined
                ? 'Loading registered namespaces…'
                : !namespaces.length && !namespaceError
                  ? 'No registered namespaces. Add a namespace before simulation.'
                  : 'Select a registered namespace.'
            }
            onChange={(event) => {
              setNamespace(event.target.value);
              setTarget('');
              setSimulation(undefined);
            }}
          >
            <SelectItem disabled hidden text="Choose a namespace" value="" />
            {namespaces?.map((entry) => (
              <SelectItem key={entry.name} text={entry.name} value={entry.name} />
            ))}
          </Select>
          <Select
            id="simulation-method"
            labelText="MCP method"
            value={method}
            disabled={busy}
            onChange={(event) => {
              setMethod(event.target.value);
              setTarget('');
              setSimulation(undefined);
            }}
          >
            <SelectItem value="tools/call" text="tools/call" />
            <SelectItem value="resources/read" text="resources/read" />
            <SelectItem value="prompts/get" text="prompts/get" />
          </Select>
          {targetError && (
            <InlineNotification
              kind="error"
              title="Registered actions are unavailable"
              subtitle={targetError}
              hideCloseButton
            />
          )}
          <Select
            id="simulation-target"
            labelText={method === 'resources/read' ? 'Resource URI' : 'Tool or prompt name'}
            value={actionTarget}
            disabled={busy || targetsLoading || !targets.length}
            helperText={
              targetsLoading
                ? 'Loading registered actions…'
                : targets.length
                  ? 'Select an action registered in this namespace.'
                  : 'No actions for this method are registered in this namespace.'
            }
            onChange={(event) => {
              setTarget(event.target.value);
              setSimulation(undefined);
            }}
          >
            <SelectItem disabled hidden text="Choose an action" value="" />
            {targets.map((entry) => (
              <SelectItem key={entry.value} text={entry.label} value={entry.value} />
            ))}
          </Select>
          {method !== 'resources/read' && (
            <TextArea
              id="simulation-arguments"
              labelText="Arguments (JSON)"
              rows={5}
              value={argumentsText}
              disabled={busy}
              onChange={(event) => setArgumentsText(event.target.value)}
            />
          )}
          <Button
            disabled={busy || !bases || !namespace || !actionTarget}
            onClick={() =>
              void run(async () => {
                setSimulation(undefined);
                setSimulation(
                  await simulate(
                    candidate(),
                    createAction(namespace, method, actionTarget, argumentsText),
                  ),
                );
              })
            }
          >
            Simulate action
          </Button>
          {simulation && <SimulationResults report={simulation} />}
        </section>
        <section aria-label="Batch replay">
          <h2>Batch replay</h2>
          <AuditEventPicker
            namespaces={namespaces ?? []}
            selected={auditIds}
            onChange={setAuditIds}
            disabled={busy || running}
          />
          <section aria-label="Synthetic actions for replay">
            <h3>Synthetic actions</h3>
            <p>
              Use the single-action controls above to choose a namespace, method, target, and
              arguments. Add each action to this batch.
            </p>
            <Button
              kind="secondary"
              disabled={busy || running || !actionTarget}
              onClick={() =>
                void run(async () => {
                  setReplayActions((actions) => [
                    ...actions,
                    createAction(namespace, method, actionTarget, argumentsText),
                  ]);
                })
              }
            >
              Add selected action to replay
            </Button>
            {replayActions.length > 0 ? (
              <table>
                <caption>Actions in this batch</caption>
                <thead>
                  <tr>
                    <th>Namespace</th>
                    <th>Action</th>
                    <th>Target</th>
                    <th>Arguments</th>
                    <th>Remove</th>
                  </tr>
                </thead>
                <tbody>
                  {replayActions.map((action, index) => {
                    const request = action.request as {
                      method: string;
                      params: { name?: string; uri?: string; arguments?: unknown };
                    };
                    return (
                      <tr key={index}>
                        <td>{action.namespace}</td>
                        <td>{request.method}</td>
                        <td>{request.params.name ?? request.params.uri}</td>
                        <td>
                          {request.params.arguments !== undefined && (
                            <details>
                              <summary>View arguments</summary>
                              <pre>{JSON.stringify(request.params.arguments, null, 2)}</pre>
                            </details>
                          )}
                        </td>
                        <td>
                          <Button
                            kind="ghost"
                            size="sm"
                            disabled={busy || running}
                            onClick={() =>
                              setReplayActions((actions) =>
                                actions.filter((_, position) => position !== index),
                              )
                            }
                          >
                            Remove action {index + 1}
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <p>No synthetic actions added.</p>
            )}
          </section>
          {auditIds.length === 0 && replayActions.length === 0 && (
            <p>Select an audit event or add a synthetic action before starting replay.</p>
          )}
          <div className="policy-simulation__buttons">
            <Button
              disabled={
                busy || !bases || running || (auditIds.length === 0 && replayActions.length === 0)
              }
              onClick={() =>
                void run(async () => {
                  setJob(
                    await replay({
                      candidate: candidate(),
                      actions: replayActions,
                      audit_event_ids: auditIds,
                    }),
                  );
                  setPolling(true);
                })
              }
            >
              Start replay
            </Button>
            {job && (
              <>
                <Button
                  kind="secondary"
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      setJob(await getReplay(job.id));
                      setPolling(true);
                    })
                  }
                >
                  Refresh replay
                </Button>
                <Button
                  kind="secondary"
                  disabled={busy || !running}
                  onClick={() =>
                    void run(async () => {
                      setJob(await cancelReplay(job.id));
                      setPolling(true);
                    })
                  }
                >
                  Cancel replay
                </Button>
                <Button
                  kind="danger"
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      await deleteReplay(job.id);
                      setJob(undefined);
                    })
                  }
                >
                  Delete replay result
                </Button>
              </>
            )}
          </div>
          {job && <ReplayResults job={job} />}
        </section>
        {busy && <InlineLoading description="Processing policy test…" />}
      </div>
    </div>
  );
};

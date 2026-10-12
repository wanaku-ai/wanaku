import { Accordion, AccordionItem, InlineNotification, Tag } from '@carbon/react';
import type { PolicyIdentity, SimulationReport, ValidationReport, ReplayJob } from '../../models';

export const ReportIdentity = ({
  identity,
  stale,
}: {
  identity: PolicyIdentity;
  stale: boolean;
}) => (
  <>
    {stale && (
      <InlineNotification
        kind="warning"
        title="Stale report"
        subtitle="The active revision changed. Run the test again before review."
        hideCloseButton
      />
    )}
    <details>
      <summary>Policy identity</summary>
      <pre>{JSON.stringify(identity, null, 2)}</pre>
    </details>
  </>
);

export const ValidationResults = ({ report }: { report: ValidationReport }) => (
  <section aria-label="Validation report">
    <h2>Validation report</h2>
    <Tag type={report.valid ? 'green' : 'red'}>{report.valid ? 'Valid' : 'Invalid'}</Tag>
    <ReportIdentity identity={report.identity} stale={report.stale} />
    {report.diagnostics.length === 0 ? (
      <p>No diagnostics.</p>
    ) : (
      <ul>
        {report.diagnostics.map((item, index) => (
          <li key={index}>
            <Tag type={item.severity === 'error' ? 'red' : 'warm-gray'}>{item.severity}</Tag>{' '}
            <code>{item.reason_code}</code> — {item.path}
          </li>
        ))}
      </ul>
    )}
  </section>
);

export const SimulationResults = ({ report }: { report: SimulationReport }) => (
  <section aria-label="Simulation report">
    <h2>Simulation report</h2>
    <Tag type={report.decision === 'allow' ? 'green' : report.decision === 'deny' ? 'red' : 'gray'}>
      {report.decision}
    </Tag>
    {report.conditional && (
      <InlineNotification
        kind="warning"
        title="Conditional result"
        subtitle="Skipped or unsupported evaluator stages can change this decision."
        hideCloseButton
      />
    )}
    <p>
      {report.namespace} · {report.operation} · {report.target ?? 'No target'} ·{' '}
      {report.duration_ms} ms
    </p>
    <p>
      Baseline used: {report.baseline_used ? 'Yes' : 'No'} · Failure behavior used:{' '}
      {report.failure_used ? 'Yes' : 'No'}
    </p>
    <ReportIdentity identity={report.identity} stale={report.stale} />
    <table>
      <caption>Decision stages</caption>
      <thead>
        <tr>
          <th>Stage</th>
          <th>Decision</th>
          <th>Reason code</th>
          <th>Matching identifiers</th>
        </tr>
      </thead>
      <tbody>
        {report.stages.map((stage, index) => (
          <tr key={index}>
            <td>{stage.stage}</td>
            <td>{stage.decision}</td>
            <td>{stage.reason_code}</td>
            <td>{stage.matched_ids.join(', ') || 'None'}</td>
          </tr>
        ))}
      </tbody>
    </table>
    <details>
      <summary>Enforcement posture and redaction</summary>
      <pre>{JSON.stringify({ posture: report.posture, redaction: report.redaction }, null, 2)}</pre>
    </details>
  </section>
);

export const ReplayResults = ({ job }: { job: ReplayJob }) => (
  <section aria-label="Replay report">
    <h2>Replay report</h2>
    <p>
      Job {job.id} · {job.status} · {job.duration_ms} ms
    </p>
    {job.reason_code && (
      <p>
        Reason: <code>{job.reason_code}</code>
      </p>
    )}
    <ReportIdentity identity={job.identity} stale={job.stale} />
    <div className="policy-simulation__counts">
      {Object.entries(job.counts).map(([category, count]) => (
        <Tag
          key={category}
          type={
            category === 'deny_to_allow'
              ? 'red'
              : category === 'allow_to_deny'
                ? 'warm-gray'
                : 'gray'
          }
        >
          {category}: {count}
        </Tag>
      ))}
    </div>
    {job.skipped_audit_ids.length > 0 && (
      <InlineNotification
        kind="warning"
        title="Skipped audit events"
        subtitle={job.skipped_audit_ids.join(', ')}
        hideCloseButton
      />
    )}
    {job.status === 'completed' && job.details.length === 0 && (
      <InlineNotification
        kind="warning"
        title="No actions were replayed"
        subtitle="Selected audit events may have expired or lack usable captured request payloads. Review the skipped event IDs."
        hideCloseButton
      />
    )}
    <Accordion>
      {job.details.map((detail) => (
        <AccordionItem key={detail.index} title={`Action ${detail.index + 1}: ${detail.category}`}>
          {detail.access_expansion && (
            <InlineNotification
              kind="warning"
              title="Access expansion"
              subtitle="The candidate allows an action that the active policy denies."
              hideCloseButton
            />
          )}
          {detail.compatibility_impact && (
            <InlineNotification
              kind="warning"
              title="Compatibility impact"
              subtitle="The candidate denies an action that the active policy allows."
              hideCloseButton
            />
          )}
          {detail.outside_evidence && <Tag type="purple">Outside supporting evidence</Tag>}
          <h3>Active policy</h3>
          <SimulationResults report={detail.active} />
          <h3>Candidate policy</h3>
          <SimulationResults report={detail.candidate} />
        </AccordionItem>
      ))}
    </Accordion>
  </section>
);

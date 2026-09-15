import { Button, Select, SelectItem, TextInput } from "@carbon/react";
import type { ChangeEvent, FormEvent } from "react";
import type { AuditFilterValues } from "./audit-utils";

interface AuditFiltersProps {
  disabled: boolean;
  values: AuditFilterValues;
  onChange: (values: AuditFilterValues) => void;
  onClear: () => void;
  onSubmit: () => void;
}

export const AuditFilters = ({ disabled, values, onChange, onClear, onSubmit }: AuditFiltersProps) => {
  const update = (field: keyof AuditFilterValues) => (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    onChange({ ...values, [field]: event.target.value });
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form className="audit-filters" aria-label="Audit event filters" data-testid="audit-filters" onSubmit={submit}>
      <Select id="audit-decision" labelText="Decision" value={values.decision} onChange={update("decision")}>
        <SelectItem value="" text="All decisions" />
        <SelectItem value="allow" text="Allow" />
        <SelectItem value="block" text="Block" />
        <SelectItem value="warn" text="Warn" />
        <SelectItem value="reject_malformed" text="Reject malformed" />
        <SelectItem value="error" text="Error" />
      </Select>
      <TextInput id="audit-operation" labelText="Operation" value={values.operation} onChange={update("operation")} />
      <TextInput id="audit-namespace" labelText="Namespace" value={values.namespace} onChange={update("namespace")} />
      <TextInput id="audit-target" labelText="Target" value={values.target} onChange={update("target")} />
      <TextInput id="audit-reason-code" labelText="Reason code" value={values.reasonCode} onChange={update("reasonCode")} />
      <TextInput id="audit-correlation-id" labelText="Correlation ID" value={values.correlationId} onChange={update("correlationId")} />
      <TextInput id="audit-from" type="datetime-local" labelText="From" value={values.from} onChange={update("from")} />
      <TextInput id="audit-to" type="datetime-local" labelText="To" value={values.to} onChange={update("to")} />
      <div className="audit-filters__actions">
        <Button type="submit" size="md" disabled={disabled}>Apply filters</Button>
        <Button type="button" kind="secondary" size="md" disabled={disabled} onClick={onClear}>Clear filters</Button>
      </div>
    </form>
  );
};

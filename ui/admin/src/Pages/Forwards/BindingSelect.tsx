import { Select, SelectItem } from "@carbon/react";
import React, { useEffect, useState } from "react";
import { useBindings } from "../../hooks/api/use-bindings";
import type { CredentialBinding, CredentialPurpose } from "../../models";

interface BindingSelectProps {
  id: string;
  labelText: string;
  helperText?: string;
  /** The forward name. A binding is usable only when its forwardId matches. */
  forwardName: string;
  /** The purpose the selected binding must be allowed to serve. */
  purpose: CredentialPurpose;
  /** The currently referenced binding id, if any. */
  value?: string;
  onChange: (bindingId: string | undefined) => void;
}

interface BindingOption {
  id: string;
  label: string;
}

const NONE_VALUE = "";

/**
 * A read-from-config binding selector for a single purpose. Credential bindings
 * are authored in wanaku.yaml (or restored from the snapshot) and are read-only
 * here; this control only references an existing binding by id. Options are the
 * bindings that this forward owns and that allow the given purpose.
 */
export const BindingSelect: React.FC<BindingSelectProps> = ({
  id,
  labelText,
  helperText,
  forwardName,
  purpose,
  value,
  onChange,
}) => {
  const { listBindings } = useBindings();
  const [bindings, setBindings] = useState<CredentialBinding[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const response = await listBindings();
        if (active && Array.isArray(response.data)) {
          setBindings(response.data);
        }
      } catch {
        // Bindings are optional. Leave the list empty when the request fails.
      } finally {
        if (active) {
          setLoaded(true);
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [listBindings]);

  const available = bindings.filter(
    (binding) =>
      binding.forwardId === forwardName &&
      (binding.allowedPurposes ?? []).includes(purpose),
  );

  const options: BindingOption[] = available.map((binding) => ({
    id: binding.id,
    label: binding.id,
  }));

  // Keep an already-referenced binding id selectable so editing never silently
  // drops it. Once the list has loaded, a value that is not in the available set
  // is not usable for this forward and purpose, so mark it as unavailable rather
  // than let it look valid (for example after the forward name is edited).
  if (value && !options.some((option) => option.id === value)) {
    options.push({ id: value, label: loaded ? `${value} (unavailable)` : value });
  }

  const disabled = loaded && available.length === 0 && !value;

  return (
    <Select
      id={id}
      labelText={labelText}
      helperText={helperText}
      value={value ?? NONE_VALUE}
      disabled={disabled}
      onChange={(event) => {
        const next = event.target.value;
        onChange(next === NONE_VALUE ? undefined : next);
      }}
    >
      <SelectItem text="None" value={NONE_VALUE} />
      {options.map((option) => (
        <SelectItem key={option.id} id={option.id} text={option.label} value={option.id} />
      ))}
    </Select>
  );
};

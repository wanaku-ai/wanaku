import { useMemo, useRef } from 'react';
import hljs from 'highlight.js/lib/core';
import yaml from 'highlight.js/lib/languages/yaml';
import './YamlEditor.scss';

const highlighter = hljs.newInstance();
highlighter.registerLanguage('yaml', yaml);

interface YamlEditorProps {
  id: string;
  labelText: string;
  helperText: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
}

export const YamlEditor = ({
  id,
  labelText,
  helperText,
  value,
  disabled,
  onChange,
}: YamlEditorProps) => {
  const preview = useRef<HTMLPreElement>(null);
  const highlighted = useMemo(
    () => highlighter.highlight(`${value}\n`, { language: 'yaml' }).value,
    [value],
  );

  return (
    <div className="yaml-editor">
      <label className="cds--label" htmlFor={id}>
        {labelText}
      </label>
      <div className="yaml-editor__input">
        <pre ref={preview} className="yaml-editor__highlight" aria-hidden="true">
          <code dangerouslySetInnerHTML={{ __html: highlighted }} />
        </pre>
        <textarea
          id={id}
          aria-describedby={`${id}-helper`}
          value={value}
          disabled={disabled}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          onChange={(event) => onChange(event.target.value)}
          onScroll={(event) => {
            if (preview.current) {
              preview.current.scrollTop = event.currentTarget.scrollTop;
              preview.current.scrollLeft = event.currentTarget.scrollLeft;
            }
          }}
        />
      </div>
      <p id={`${id}-helper`} className="cds--form__helper-text">
        {helperText}
      </p>
    </div>
  );
};

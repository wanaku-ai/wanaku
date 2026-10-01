import { useCallback, useEffect, useState } from 'react';
import { ErrorNotification } from '../../components/ErrorNotification';
import { PageSkeleton } from '../../components/PageSkeleton';
import { useBindings } from '../../hooks/api/use-bindings';
import type { CredentialBinding } from '../../models';
import { BindingDetailModal } from './BindingDetailModal';
import { BindingsTable } from './BindingsTable';
import { sortedBindings } from './bindings';

type BindingsState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; data: CredentialBinding[] };

const errorMessage = (error: unknown, fallback: string): string =>
  error instanceof Error ? error.message : fallback;

export const BindingsPage = () => {
  const { listBindings } = useBindings();
  const [bindingsState, setBindingsState] = useState<BindingsState>({ status: 'loading' });
  const [selectedBinding, setSelectedBinding] = useState<CredentialBinding>();

  const loadBindings = useCallback(async () => {
    setBindingsState({ status: 'loading' });
    try {
      const response = await listBindings();
      setBindingsState({ status: 'success', data: sortedBindings(response.data) });
    } catch (error) {
      setBindingsState({
        status: 'error',
        message: errorMessage(error, 'Failed to load credential bindings'),
      });
    }
  }, [listBindings]);

  useEffect(() => {
    void loadBindings();
  }, [loadBindings]);

  if (bindingsState.status === 'loading') return <PageSkeleton title="Credential Bindings" />;

  return (
    <div>
      <h1 className="title" data-testid="bindings-page-title">
        Credential Bindings
      </h1>
      <p className="description">
        Credential bindings connect a forward to the credentials it is allowed to use for outbound
        requests. Bindings are created and managed by operators outside this UI; this view is
        read-only.
      </p>
      <div id="page-content">
        {bindingsState.status === 'error' ? (
          <ErrorNotification
            errorMessage={bindingsState.message}
            onClose={() => void loadBindings()}
          />
        ) : (
          <BindingsTable bindings={bindingsState.data} onViewDetails={setSelectedBinding} />
        )}
      </div>
      {selectedBinding && (
        <BindingDetailModal
          binding={selectedBinding}
          onRequestClose={() => setSelectedBinding(undefined)}
        />
      )}
    </div>
  );
};

import { ComposedModal, ModalBody, ModalHeader, Tag } from '@carbon/react';
import React from 'react';
import type { CredentialBinding } from '../../models';
import { formatMechanism } from './bindings';

interface BindingDetailModalProps {
  binding: CredentialBinding;
  onRequestClose: () => void;
}

export const BindingDetailModal: React.FC<BindingDetailModalProps> = ({
  binding,
  onRequestClose,
}) => {
  const restrictions = binding.restrictions;
  const hasRestrictions =
    !!restrictions &&
    [
      restrictions.namespaces,
      restrictions.operations,
      restrictions.identities,
      restrictions.governed_items,
    ].some((value) => !!value && value.length > 0);

  return (
    <ComposedModal open onClose={onRequestClose} data-testid="binding-detail-modal">
      <ModalHeader
        title="Credential binding details"
        label={binding.id}
        closeModal={onRequestClose}
      />
      <ModalBody>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <strong>Forward:</strong> {binding.forwardId}
          </div>
          <div>
            <strong>Origin:</strong> {binding.origin}
          </div>
          <div>
            <strong>Mechanism:</strong> {formatMechanism(binding.mechanism)}
          </div>
          <div>
            <strong>Allowed purposes:</strong>{' '}
            {(binding.allowedPurposes ?? []).map((purpose, index) => (
              <Tag
                key={`${purpose}-${index}`}
                type="blue"
                size="sm"
                style={{ marginRight: '0.25rem' }}
              >
                {purpose}
              </Tag>
            ))}
          </div>
          <div>
            <strong>Secret references:</strong>{' '}
            {(binding.secretRefs ?? []).map((ref, index) => (
              <Tag key={`${ref}-${index}`} type="gray" size="sm" style={{ marginRight: '0.25rem' }}>
                {ref}
              </Tag>
            ))}
          </div>
          <div>
            <strong>Revision:</strong> {binding.revision ?? '—'}
          </div>

          {hasRestrictions && (
            <>
              <h4 style={{ marginBottom: 0 }}>Restrictions</h4>
              {!!restrictions?.namespaces?.length && (
                <div>
                  <strong>Namespaces:</strong> {restrictions.namespaces.join(', ')}
                </div>
              )}
              {!!restrictions?.operations?.length && (
                <div>
                  <strong>Operations:</strong> {restrictions.operations.join(', ')}
                </div>
              )}
              {!!restrictions?.identities?.length && (
                <div>
                  <strong>Identities:</strong> {restrictions.identities.join(', ')}
                </div>
              )}
              {!!restrictions?.governed_items?.length && (
                <div>
                  <strong>Governed items:</strong> {restrictions.governed_items.join(', ')}
                </div>
              )}
            </>
          )}

          {binding.cache?.maxTtlSeconds !== undefined && (
            <div>
              <strong>Cache max TTL (seconds):</strong> {binding.cache.maxTtlSeconds}
            </div>
          )}
        </div>
      </ModalBody>
    </ComposedModal>
  );
};

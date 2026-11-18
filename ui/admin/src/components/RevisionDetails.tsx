import { Tag } from '@carbon/react';
import type { RevisionMetadata } from '../models';
import { formatDate, statusTagType } from '../utils/revision-format';
import './RevisionDetails.scss';

/** The metadata of one configuration revision: status, origin, dates, checksum, actor, failure. */
export const RevisionDetails = ({ revision }: { revision: RevisionMetadata }) => (
  <div className="revision-details">
    <div>
      <span>Status</span>
      <Tag type={statusTagType(revision.status)}>{revision.status}</Tag>
    </div>
    <div>
      <span>Origin</span>
      <strong>{revision.origin}</strong>
    </div>
    <div>
      <span>Created</span>
      <strong>{formatDate(revision.created_at)}</strong>
    </div>
    <div>
      <span>Activated</span>
      <strong>{formatDate(revision.activated_at)}</strong>
    </div>
    <div>
      <span>Checksum</span>
      <code>{revision.checksum}</code>
    </div>
    {revision.actor && (
      <div>
        <span>Actor</span>
        <strong>{revision.actor}</strong>
      </div>
    )}
    {revision.failure_reason && (
      <div>
        <span>Failure reason</span>
        <strong>{revision.failure_reason}</strong>
      </div>
    )}
  </div>
);

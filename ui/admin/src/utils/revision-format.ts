import type { RevisionMetadata } from '../models';

export const formatDate = (value?: string | null): string =>
  value
    ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : 'Not activated';

export const statusTagType = (status: RevisionMetadata['status']): 'green' | 'gray' | 'red' => {
  if (status === 'active') return 'green';
  if (status === 'rejected') return 'red';
  return 'gray';
};

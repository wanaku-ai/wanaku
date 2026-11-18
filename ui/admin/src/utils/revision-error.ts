import { ApiError } from '../custom-fetch';

/**
 * The meaning of a failed revision request. `not_found` is a missing revision, `conflict` is a
 * stale expected revision, `validation` is a rejected configuration, `invalid_request` is a
 * malformed request, and `unexpected` is any other failure.
 */
export type RevisionErrorKind =
  | 'not_found'
  | 'conflict'
  | 'validation'
  | 'invalid_request'
  | 'unexpected';

/** Classifies a failure by HTTP status only. It never uses the error message. */
export const revisionErrorKind = (error: unknown): RevisionErrorKind => {
  if (!(error instanceof ApiError)) return 'unexpected';
  switch (error.status) {
    case 400:
      return 'invalid_request';
    case 404:
      return 'not_found';
    case 409:
      return 'conflict';
    case 422:
      return 'validation';
    default:
      return 'unexpected';
  }
};

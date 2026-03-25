import { Format } from '@stoplight/spectral-core';
import type { ErrorObject } from 'ajv';
export declare function prepareResults(errors: ErrorObject[]): void;
export declare function getSchema(formats: Set<Format>, resolved: boolean): Record<string, any> | void;
export declare const asyncApiDocumentSchema: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, {
    resolved: boolean;
}>;

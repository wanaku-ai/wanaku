import type { JsonPath } from '@stoplight/types';
declare type OperationObject = Record<string, unknown>;
declare type AsyncAPI = {
    channels?: Record<string, {
        subscribe?: OperationObject;
        publish?: OperationObject;
    }>;
};
declare type Result = {
    path: JsonPath;
    kind: 'subscribe' | 'publish';
    operation: OperationObject;
};
export declare function getAllOperations(asyncapi: AsyncAPI): IterableIterator<Result>;
export {};

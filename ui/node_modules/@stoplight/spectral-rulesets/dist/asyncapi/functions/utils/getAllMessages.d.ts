import type { JsonPath } from '@stoplight/types';
declare type MessageObject = Record<string, unknown>;
declare type AsyncAPI = {
    channels?: Record<string, {
        subscribe?: Record<string, unknown>;
        publish?: Record<string, unknown>;
    }>;
};
declare type Result = {
    path: JsonPath;
    message: MessageObject;
};
export declare function getAllMessages(asyncapi: AsyncAPI): IterableIterator<Result>;
export {};

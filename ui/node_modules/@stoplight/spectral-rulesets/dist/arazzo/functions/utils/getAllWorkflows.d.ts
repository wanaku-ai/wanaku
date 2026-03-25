import type { JsonPath } from '@stoplight/types';
import { ArazzoSpecification, Workflow } from '../types/arazzoTypes';
declare type Result = {
    path: JsonPath;
    workflow: Workflow;
};
export declare function getAllWorkflows(arazzo: ArazzoSpecification): IterableIterator<Result>;
export {};

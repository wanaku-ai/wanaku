declare type HaveTraits = {
    traits?: any[];
} & Record<string, any>;
export declare function mergeTraits<T extends HaveTraits>(data: T): T;
export {};

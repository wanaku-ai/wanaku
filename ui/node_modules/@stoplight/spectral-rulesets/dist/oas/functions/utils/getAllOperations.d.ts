declare type Item = {
    path: string;
    operation: string;
    value: unknown;
};
export declare function getAllOperations(paths: unknown): IterableIterator<Item>;
export {};

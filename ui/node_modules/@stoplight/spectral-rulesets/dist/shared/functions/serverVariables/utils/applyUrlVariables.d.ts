declare type Variable = readonly [name: string, values: readonly string[]];
export declare function applyUrlVariables(url: string, variables: readonly Variable[]): Iterable<string>;
export {};

export declare type Options = {
    type: 'default' | 'examples';
};
declare type SchemaFragment = {
    default?: unknown;
    examples?: unknown[];
};
declare const _default: import("@stoplight/spectral-core").RulesetFunctionWithValidator<SchemaFragment, Options>;
export default _default;

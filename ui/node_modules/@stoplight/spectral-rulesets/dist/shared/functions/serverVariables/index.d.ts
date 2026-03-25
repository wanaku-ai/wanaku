declare type Input = {
    url: string;
    variables?: Record<string, {
        enum: string[] | never;
        default: string | never;
        description: string | never;
        examples: string | never;
        [key: string]: unknown;
    }>;
};
declare type Options = {
    checkSubstitutions?: boolean;
    requireDefault?: boolean;
} | null;
declare const _default: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Input, Options>;
export default _default;

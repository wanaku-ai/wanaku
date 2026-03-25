interface MessageExample {
    name?: string;
    summary?: string;
    payload?: unknown;
    headers?: unknown;
}
export interface MessageFragment {
    payload: unknown;
    headers: unknown;
    traits?: any[];
    examples?: MessageExample[];
}
declare const _default: import("@stoplight/spectral-core").RulesetFunctionWithValidator<MessageFragment, null>;
export default _default;

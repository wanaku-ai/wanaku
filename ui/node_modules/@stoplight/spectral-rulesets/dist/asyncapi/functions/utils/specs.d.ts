declare const specs: {
    '2.0.0': import("json-schema").JSONSchema7;
    '2.1.0': import("json-schema").JSONSchema7;
    '2.2.0': import("json-schema").JSONSchema7;
    '2.3.0': import("json-schema").JSONSchema7;
    '2.4.0': import("json-schema").JSONSchema7;
    '2.5.0': import("json-schema").JSONSchema7;
    '2.6.0': import("json-schema").JSONSchema7;
    '3.0.0': import("json-schema").JSONSchema7;
};
export declare type AsyncAPISpecVersion = keyof typeof specs;
export declare const latestVersion: string;
export declare function getCopyOfSchema(version: AsyncAPISpecVersion): Record<string, unknown>;
export {};

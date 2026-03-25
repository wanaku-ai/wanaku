export { ruleset as default };
declare const ruleset: {
    documentationUrl: string;
    formats: import("@stoplight/spectral-core").Format<void>[];
    aliases: {
        PathItem: string[];
        OperationObject: string[];
        SecurityRequirementObject: string[];
        ResponseObject: {
            targets: {
                formats: import("@stoplight/spectral-core").Format<void>[];
                given: string[];
            }[];
        };
        LinkObject: {
            targets: {
                formats: import("@stoplight/spectral-core").Format<void>[];
                given: string[];
            }[];
        };
        ArrayProperties: {
            targets: {
                formats: import("@stoplight/spectral-core").Format<void>[];
                given: string[];
            }[];
        };
    };
    rules: {
        'operation-success-response': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, null>;
            };
        };
        'oas2-operation-formData-consume-check': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    consumes: unknown[];
                    parameters: unknown[];
                }, null>;
            };
        };
        'operation-operationId-unique': {
            description: string;
            recommended: boolean;
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, null>;
            };
        };
        'operation-parameters': {
            description: string;
            message: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown[], null>;
            };
        };
        'operation-tag-defined': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, null>;
            };
        };
        'path-params': {
            description: string;
            message: string;
            severity: number;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, null>;
            };
        };
        'contact-properties': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            }[];
        };
        'duplicated-entry-in-enum': {
            description: string;
            severity: string;
            recommended: boolean;
            message: string;
            given: string[];
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, import("./functions/oasSchema").Options>;
                functionOptions: {
                    schema: {
                        type: string;
                        uniqueItems: boolean;
                    };
                };
            };
        };
        'info-contact': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'info-description': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'info-license': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'license-url': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'no-eval-in-markdown': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'no-script-tags-in-markdown': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'openapi-tags-alphabetical': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown> | unknown[], import("@stoplight/spectral-functions").AlphabeticalOptions>;
                functionOptions: {
                    keyedBy: string;
                };
            };
        };
        'openapi-tags-uniqueness': {
            description: string;
            message: string;
            severity: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    name: string;
                }[], null>;
            };
        };
        'openapi-tags': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, import("@stoplight/spectral-functions").SchemaOptions>;
                functionOptions: {
                    dialect: string;
                    schema: {
                        type: string;
                        minItems: number;
                    };
                };
            };
        };
        'operation-description': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'operation-operationId': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'operation-operationId-valid-in-url': {
            message: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    match: string;
                };
            };
        };
        'operation-singular-tag': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string | number | Record<string, unknown> | unknown[], import("@stoplight/spectral-functions").LengthOptions>;
                functionOptions: {
                    max: number;
                };
            };
        };
        'operation-tags': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, import("@stoplight/spectral-functions").SchemaOptions>;
                functionOptions: {
                    dialect: string;
                    schema: {
                        type: string;
                        minItems: number;
                    };
                };
            };
        };
        'path-declarations-must-exist': {
            message: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'path-keys-no-trailing-slash': {
            message: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'path-not-include-query': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'tag-description': {
            description: string;
            recommended: boolean;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'no-$ref-siblings': {
            formats: import("@stoplight/spectral-core").Format<void>[];
            description: string;
            message: string;
            severity: number;
            recommended: boolean;
            resolved: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'array-items': {
            message: string;
            severity: number;
            recommended: boolean;
            resolved: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
                field: string;
            };
        };
        'typed-enum': {
            description: string;
            message: string;
            recommended: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    [key: string]: unknown;
                    enum: unknown[];
                    type: string | string[];
                }, null>;
            };
        };
        'oas2-api-host': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'oas2-api-schemes': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, import("@stoplight/spectral-functions").SchemaOptions>;
                functionOptions: {
                    dialect: string;
                    schema: {
                        items: {
                            type: string;
                        };
                        minItems: number;
                        type: string;
                    };
                };
            };
        };
        'oas2-discriminator': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            severity: number;
            message: string;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    [key: string]: unknown;
                    discriminator: string;
                }, null>;
            };
        };
        'oas2-host-not-example': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'oas2-host-trailing-slash': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'oas2-parameter-description': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'oas2-operation-security-defined': {
            description: string;
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, string[]>, {
                    oasVersion: 2 | 3;
                }>;
                functionOptions: {
                    oasVersion: number;
                };
            };
        };
        'oas2-valid-schema-example': {
            description: string;
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            severity: number;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("./functions/oasExample").Options>;
                functionOptions: {
                    schemaField: string;
                    oasVersion: number;
                    type: string;
                };
            };
        };
        'oas2-valid-media-example': {
            description: string;
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("./functions/oasExample").Options>;
                functionOptions: {
                    schemaField: string;
                    oasVersion: number;
                    type: string;
                };
            };
        };
        'oas2-anyOf': {
            message: string;
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, unknown>;
            };
        };
        'oas2-oneOf': {
            message: string;
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, unknown>;
            };
        };
        'oas2-schema': {
            description: string;
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            severity: number;
            resolved: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'oas2-unused-definition': {
            description: string;
            recommended: boolean;
            resolved: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("@stoplight/spectral-functions").UnreferencedReusableObjectOptions>;
                functionOptions: {
                    reusableObjectsLocation: string;
                };
            };
        };
        'oas3-api-servers': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, import("@stoplight/spectral-functions").SchemaOptions>;
                functionOptions: {
                    dialect: string;
                    schema: {
                        items: {
                            type: string;
                        };
                        minItems: number;
                        type: string;
                    };
                };
            };
        };
        'oas3-examples-value-or-externalValue': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("@stoplight/spectral-functions").XorOptions>;
                functionOptions: {
                    properties: string[];
                };
            };
        };
        'oas3-operation-security-defined': {
            description: string;
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, string[]>, {
                    oasVersion: 2 | 3;
                }>;
                functionOptions: {
                    oasVersion: number;
                };
            };
        };
        'oas3-parameter-description': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string[];
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'oas3-server-not-example.com': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'oas3-server-trailing-slash': {
            description: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'oas3-valid-media-example': {
            description: string;
            message: string;
            recommended: boolean;
            severity: number;
            formats: import("@stoplight/spectral-core").Format<void>[];
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("./functions/oasExample").Options>;
                functionOptions: {
                    schemaField: string;
                    oasVersion: number;
                    type: string;
                };
            };
        };
        'oas3-valid-schema-example': {
            description: string;
            message: string;
            severity: number;
            formats: import("@stoplight/spectral-core").Format<void>[];
            recommended: boolean;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<Record<string, unknown>, import("./functions/oasExample").Options>;
                functionOptions: {
                    schemaField: string;
                    oasVersion: number;
                    type: string;
                };
            };
        };
        'oas3-schema': {
            description: string;
            message: string;
            severity: number;
            formats: import("@stoplight/spectral-core").Format<void>[];
            recommended: boolean;
            resolved: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'oas3-unused-component': {
            message: string;
            recommended: boolean;
            formats: import("@stoplight/spectral-core").Format<void>[];
            resolved: boolean;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    components: Record<string, unknown>;
                }, null>;
            };
        };
        'oas3-server-variables': {
            description: string;
            message: string;
            severity: number;
            recommended: boolean;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    url: string;
                    variables?: Record<string, {
                        [key: string]: unknown;
                        enum: string[];
                        default: string;
                        description: string;
                        examples: string;
                    }> | undefined;
                }, {
                    checkSubstitutions?: boolean | undefined;
                    requireDefault?: boolean | undefined;
                } | null>;
                functionOptions: {
                    checkSubstitutions: boolean;
                    requireDefault: boolean;
                };
            };
        };
        'oas3-callbacks-in-callbacks': {
            message: string;
            formats: import("@stoplight/spectral-core").Format<void>[];
            recommended: boolean;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, unknown>;
            };
        };
        'oas3_1-servers-in-webhook': {
            message: string;
            formats: import("@stoplight/spectral-core").Format<void>[];
            recommended: boolean;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, unknown>;
            };
        };
        'oas3_1-callbacks-in-webhook': {
            message: string;
            formats: import("@stoplight/spectral-core").Format<void>[];
            recommended: boolean;
            given: string[];
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, unknown>;
            };
        };
    };
};

"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const spectral_functions_1 = require("@stoplight/spectral-functions");
const asyncApiChannelParameters_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApiChannelParameters"));
const asyncApi2ChannelServers_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApi2ChannelServers"));
const asyncApiDocumentSchema_1 = require("./functions/asyncApiDocumentSchema");
const asyncApi2MessageExamplesValidation_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApi2MessageExamplesValidation"));
const asyncApi2MessageIdUniqueness_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApi2MessageIdUniqueness"));
const asyncApi2OperationIdUniqueness_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApi2OperationIdUniqueness"));
const asyncApiSchemaValidation_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApiSchemaValidation"));
const asyncApiPayloadValidation_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApiPayloadValidation"));
const serverVariables_1 = (0, tslib_1.__importDefault)(require("../shared/functions/serverVariables"));
const functions_1 = require("../shared/functions");
const asyncApiSecurity_1 = (0, tslib_1.__importDefault)(require("./functions/asyncApiSecurity"));
const specs_1 = require("./functions/utils/specs");
exports.default = {
    documentationUrl: 'https://meta.stoplight.io/docs/spectral/docs/reference/asyncapi-rules.md',
    formats: [spectral_formats_1.aas2_0, spectral_formats_1.aas2_1, spectral_formats_1.aas2_2, spectral_formats_1.aas2_3, spectral_formats_1.aas2_4, spectral_formats_1.aas2_5, spectral_formats_1.aas2_6, spectral_formats_1.aas3_0],
    rules: {
        'asyncapi-channel-no-empty-parameter': {
            description: 'Channel path must not have empty parameter substitution pattern.',
            recommended: true,
            given: '$.channels',
            formats: [spectral_formats_1.aas2],
            then: {
                field: '@key',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '{}',
                },
            },
        },
        'asyncapi-3-channel-no-empty-parameter': {
            description: 'Channel address must not have empty parameter substitution pattern.',
            recommended: true,
            given: '$.channels.*',
            formats: [spectral_formats_1.aas3],
            then: {
                field: 'address',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '{}',
                },
            },
        },
        'asyncapi-channel-no-query-nor-fragment': {
            description: 'Channel path must not include query ("?") or fragment ("#") delimiter.',
            recommended: true,
            given: '$.channels',
            formats: [spectral_formats_1.aas2],
            then: {
                field: '@key',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '[\\?#]',
                },
            },
        },
        'asyncapi-3-channel-no-query-nor-fragment': {
            description: 'Channel address must not include query ("?") or fragment ("#") delimiter.',
            recommended: true,
            given: '$.channels.*',
            formats: [spectral_formats_1.aas3],
            then: {
                field: 'address',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '[\\?#]',
                },
            },
        },
        'asyncapi-channel-no-trailing-slash': {
            description: 'Channel path must not end with slash.',
            recommended: true,
            given: '$.channels',
            formats: [spectral_formats_1.aas2],
            then: {
                field: '@key',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '.+\\/$',
                },
            },
        },
        'asyncapi-3-channel-no-trailing-slash': {
            description: 'Channel address must not end with slash.',
            recommended: true,
            given: '$.channels.*',
            formats: [spectral_formats_1.aas3],
            then: {
                field: 'address',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '.+\\/$',
                },
            },
        },
        'asyncapi-channel-parameters': {
            description: 'Channel parameters must be defined and there must be no redundant parameters.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: ['$.channels.*', '$.components.channels.*'],
            then: {
                function: asyncApiChannelParameters_1.default,
            },
        },
        'asyncapi-channel-servers': {
            description: 'Channel servers must be defined in the "servers" object.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$',
            then: {
                function: asyncApi2ChannelServers_1.default,
            },
        },
        'asyncapi-3-channel-servers': {
            description: 'Channel servers must be defined in the "servers" object.',
            severity: 'error',
            recommended: true,
            resolved: false,
            given: '$.channels.*',
            formats: [spectral_formats_1.aas3],
            then: {
                field: '$.servers.*.$ref',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    match: '#\\/servers\\/',
                },
            },
        },
        'asyncapi-headers-schema-type-object': {
            description: 'Headers schema type must be "object".',
            message: 'Headers schema type must be "object" ({{error}}).',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.messageTraits.*.headers',
                '$.components.messages.*.headers',
                '$.channels.*.[publish,subscribe].message.headers',
                '$.channels.*.[publish,subscribe].message.traits[*].headers',
            ],
            then: {
                function: spectral_functions_1.schema,
                functionOptions: {
                    allErrors: true,
                    schema: {
                        type: 'object',
                        properties: {
                            type: {
                                enum: ['object'],
                            },
                        },
                        required: ['type'],
                    },
                },
            },
        },
        'asyncapi-3-headers-schema-type-object': {
            description: 'Headers schema type must be "object".',
            message: 'Headers schema type must be "object" ({{error}}).',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: [
                '$.components.messageTraits.*.headers',
                '$.components.messages.*.headers',
                '$.channels.*.messages.*.headers',
                '$.channels.*.messages.*.traits[*].headers',
            ],
            then: {
                function: spectral_functions_1.schema,
                functionOptions: {
                    allErrors: true,
                    schema: {
                        type: 'object',
                        properties: {
                            type: {
                                enum: ['object'],
                            },
                        },
                        required: ['type'],
                    },
                },
            },
        },
        'asyncapi-info-contact-properties': {
            description: 'Contact object must have "name", "url" and "email".',
            recommended: true,
            given: '$.info.contact',
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            then: [
                {
                    field: 'name',
                    function: spectral_functions_1.truthy,
                },
                {
                    field: 'url',
                    function: spectral_functions_1.truthy,
                },
                {
                    field: 'email',
                    function: spectral_functions_1.truthy,
                },
            ],
        },
        'asyncapi-info-contact': {
            description: 'Info object must have "contact" object.',
            recommended: true,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: '$',
            then: {
                field: 'info.contact',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-info-description': {
            description: 'Info "description" must be present and non-empty string.',
            recommended: true,
            given: '$',
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            then: {
                field: 'info.description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-info-license-url': {
            description: 'License object must include "url".',
            recommended: false,
            given: '$',
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            then: {
                field: 'info.license.url',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-info-license': {
            description: 'Info object must have "license" object.',
            recommended: true,
            given: '$',
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            then: {
                field: 'info.license',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-latest-version': {
            description: 'Checking if the AsyncAPI document is using the latest version.',
            message: `The latest version is not used. You should update to the "${specs_1.latestVersion}" version.`,
            recommended: true,
            severity: 'info',
            given: '$.asyncapi',
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            then: {
                function: spectral_functions_1.schema,
                functionOptions: {
                    schema: {
                        const: specs_1.latestVersion,
                    },
                },
            },
        },
        'asyncapi-message-examples': {
            description: 'Examples of message object should follow by "payload" and "headers" schemas.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.channels.*.[publish,subscribe].message',
                '$.channels.*.[publish,subscribe].message.oneOf.*',
                '$.components.channels.*.[publish,subscribe].message',
                '$.components.channels.*.[publish,subscribe].message.oneOf.*',
                '$.components.messages.*',
                '$.channels.*.[publish,subscribe].message.traits.*',
                '$.channels.*.[publish,subscribe].message.oneOf.*.traits.*',
                '$.components.channels.*.[publish,subscribe].message.traits.*',
                '$.components.channels.*.[publish,subscribe].message.oneOf.*.traits.*',
                '$.components.messages.*.traits.*',
                '$.components.messageTraits.*',
            ],
            then: {
                function: asyncApi2MessageExamplesValidation_1.default,
            },
        },
        'asyncapi-message-messageId-uniqueness': {
            description: '"messageId" must be unique across all the messages.',
            severity: 'error',
            recommended: true,
            given: '$',
            formats: [spectral_formats_1.aas2],
            then: {
                function: asyncApi2MessageIdUniqueness_1.default,
            },
        },
        'asyncapi-operation-description': {
            description: 'Operation "description" must be present and non-empty string.',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.channels[*][publish,subscribe]',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-3-operation-description': {
            description: 'Operation "description" must be present and non-empty string.',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: '$.operations.*',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-operation-operationId-uniqueness': {
            description: '"operationId" must be unique across all the operations.',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$',
            then: {
                function: asyncApi2OperationIdUniqueness_1.default,
            },
        },
        'asyncapi-operation-operationId': {
            description: 'Operation must have "operationId".',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.channels[*][publish,subscribe]',
            then: {
                field: 'operationId',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-operation-security': {
            description: 'Operation have to reference a defined security schemes.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.channels[*][publish,subscribe].security.*',
            then: {
                function: asyncApiSecurity_1.default,
                functionOptions: {
                    objectType: 'Operation',
                },
            },
        },
        'asyncapi-3-operation-security': {
            description: 'Operation have to reference a defined security schemes.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: '$.operations.*.security.*',
            then: {
                function: asyncApiSecurity_1.default,
                functionOptions: {
                    objectType: 'Operation',
                },
            },
        },
        'asyncapi-parameter-description': {
            description: 'Parameter objects must have "description".',
            recommended: false,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: ['$.components.parameters.*', '$.channels.*.parameters.*'],
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-payload-default': {
            description: 'Default must be valid against its defined schema.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.messageTraits[?(@.schemaFormat === void 0)].payload.default^',
                '$.components.messages[?(@.schemaFormat === void 0)].payload.default^',
                "$.channels[*][publish,subscribe][?(@property === 'message' && @.schemaFormat === void 0)].payload.default^",
            ],
            then: {
                function: asyncApiSchemaValidation_1.default,
                functionOptions: {
                    type: 'default',
                },
            },
        },
        'asyncapi-payload-examples': {
            description: 'Examples must be valid against their defined schema.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.messageTraits[?(@.schemaFormat === void 0)].payload.examples^',
                '$.components.messages[?(@.schemaFormat === void 0)].payload.examples^',
                "$.channels[*][publish,subscribe][?(@property === 'message' && @.schemaFormat === void 0)].payload.examples^",
            ],
            then: {
                function: asyncApiSchemaValidation_1.default,
                functionOptions: {
                    type: 'examples',
                },
            },
        },
        'asyncapi-payload-unsupported-schemaFormat': {
            description: 'Message schema validation is only supported with default unspecified "schemaFormat".',
            severity: 'info',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: ['$.components.messageTraits.*', '$.components.messages.*', '$.channels[*][publish,subscribe].message'],
            then: {
                field: 'schemaFormat',
                function: spectral_functions_1.undefined,
            },
        },
        'asyncapi-3-payload-unsupported-schemaFormat': {
            description: 'Message schema validation is only supported with default unspecified "schemaFormat".',
            severity: 'info',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: ['$.components.messages.*.payload', '$.channels.*.messages.*.payload'],
            then: {
                field: 'schemaFormat',
                function: spectral_functions_1.undefined,
            },
        },
        'asyncapi-payload': {
            description: 'Payloads must be valid against AsyncAPI Schema object.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.messageTraits[?(@.schemaFormat === void 0)].payload',
                '$.components.messages[?(@.schemaFormat === void 0)].payload',
                "$.channels[*][publish,subscribe][?(@property === 'message' && @.schemaFormat === void 0)].payload",
            ],
            then: {
                function: asyncApiPayloadValidation_1.default,
            },
        },
        'asyncapi-schema-default': {
            description: 'Default must be valid against its defined schema.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.schemas.*.default^',
                '$.components.parameters.*.schema.default^',
                '$.channels.*.parameters.*.schema.default^',
            ],
            then: {
                function: asyncApiSchemaValidation_1.default,
                functionOptions: {
                    type: 'default',
                },
            },
        },
        'asyncapi-schema-examples': {
            description: 'Examples must be valid against their defined schema.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.components.schemas.*.examples^',
                '$.components.parameters.*.schema.examples^',
                '$.channels.*.parameters.*.schema.examples^',
            ],
            then: {
                function: asyncApiSchemaValidation_1.default,
                functionOptions: {
                    type: 'examples',
                },
            },
        },
        'asyncapi-schema': {
            description: 'Validate structure of AsyncAPI specification.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$',
            then: {
                function: asyncApiDocumentSchema_1.asyncApiDocumentSchema,
                functionOptions: {
                    resolved: true,
                },
            },
        },
        'asyncapi-server-variables': {
            description: 'Server variables must be defined and there must be no redundant variables.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: ['$.servers.*', '$.components.servers.*'],
            then: {
                function: serverVariables_1.default,
            },
        },
        'asyncapi-server-no-empty-variable': {
            description: 'Server URL must not have empty variable substitution pattern.',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.servers[*].url',
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '{}',
                },
            },
        },
        'asyncapi-3-server-no-empty-variable': {
            description: 'Server host and pathname must not have empty variable substitution pattern.',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: ['$.servers[*].host', '$.servers[*].pathname'],
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '{}',
                },
            },
        },
        'asyncapi-server-no-trailing-slash': {
            description: 'Server URL must not end with slash.',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.servers[*].url',
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '/$',
                },
            },
        },
        'asyncapi-3-server-no-trailing-slash': {
            description: 'Server host must not end with slash.',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: '$.servers.*.host',
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '/$',
                },
            },
        },
        'asyncapi-server-not-example-com': {
            description: 'Server URL must not point at example.com.',
            recommended: false,
            given: '$.servers[*].url',
            formats: [spectral_formats_1.aas2],
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: 'example\\.com',
                },
            },
        },
        'asyncapi-3-server-not-example-com': {
            description: 'Server host must not point at example.com.',
            recommended: false,
            given: '$.servers.*.host',
            formats: [spectral_formats_1.aas3],
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: 'example\\.com',
                },
            },
        },
        'asyncapi-server-security': {
            description: 'Server have to reference a defined security schemes.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$.servers.*.security.*',
            then: {
                function: asyncApiSecurity_1.default,
                functionOptions: {
                    objectType: 'Server',
                },
            },
        },
        'asyncapi-servers': {
            description: 'AsyncAPI object must have non-empty "servers" object.',
            recommended: true,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: '$',
            then: {
                field: 'servers',
                function: spectral_functions_1.schema,
                functionOptions: {
                    schema: {
                        type: 'object',
                        minProperties: 1,
                    },
                    allErrors: true,
                },
            },
        },
        'asyncapi-tag-description': {
            description: 'Tag object must have "description".',
            recommended: false,
            formats: [spectral_formats_1.aas2],
            given: '$.tags[*]',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-3-tag-description': {
            description: 'Tag object must have "description".',
            recommended: false,
            formats: [spectral_formats_1.aas3],
            given: '$.info.tags[*]',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-tags-alphabetical': {
            description: 'AsyncAPI object must have alphabetical "tags".',
            recommended: false,
            given: '$',
            formats: [spectral_formats_1.aas2],
            then: {
                field: 'tags',
                function: spectral_functions_1.alphabetical,
                functionOptions: {
                    keyedBy: 'name',
                },
            },
        },
        'asyncapi-3-tags-alphabetical': {
            description: 'AsyncAPI object must have alphabetical "tags".',
            recommended: false,
            given: '$.info',
            formats: [spectral_formats_1.aas3],
            then: {
                field: 'tags',
                function: spectral_functions_1.alphabetical,
                functionOptions: {
                    keyedBy: 'name',
                },
            },
        },
        'asyncapi-tags-uniqueness': {
            description: 'Each tag must have a unique name.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: [
                '$.tags',
                '$.servers.*.tags',
                '$.components.servers.*.tags',
                '$.channels.*.[publish,subscribe].tags',
                '$.components.channels.*.[publish,subscribe].tags',
                '$.channels.*.[publish,subscribe].traits.*.tags',
                '$.components.channels.*.[publish,subscribe].traits.*.tags',
                '$.components.operationTraits.*.tags',
                '$.channels.*.[publish,subscribe].message.tags',
                '$.channels.*.[publish,subscribe].message.oneOf.*.tags',
                '$.components.channels.*.[publish,subscribe].message.tags',
                '$.components.channels.*.[publish,subscribe].message.oneOf.*.tags',
                '$.components.messages.*.tags',
                '$.channels.*.[publish,subscribe].message.traits.*.tags',
                '$.channels.*.[publish,subscribe].message.oneOf.*.traits.*.tags',
                '$.components.channels.*.[publish,subscribe].message.traits.*.tags',
                '$.components.channels.*.[publish,subscribe].message.oneOf.*.traits.*.tags',
                '$.components.messages.*.traits.*.tags',
                '$.components.messageTraits.*.tags',
            ],
            then: {
                function: functions_1.uniquenessTags,
            },
        },
        'asyncapi-3-tags-uniqueness': {
            description: 'Each tag must have a unique name.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: [
                '$.info.tags',
                '$.servers.*.tags',
                '$.components.servers.*.tags',
                '$.operations.*.tags',
                '$.components.operations.*.tags',
                '$.operations.*.traits.*.tags',
                '$.components.operations.traits.*.tags',
                '$.components.operationTraits.*.tags',
                '$.channels.*.messages.*.tags',
                '$.components.channels.*.messages.tags',
                '$.components.messages.*.tags',
                '$.channels.*..messages.*.traits.*.tags',
                '$.components.channels.*.messages.*.traits.*.tags',
                '$.components.messages.*.traits.*.tags',
                '$.components.messageTraits.*.tags',
            ],
            then: {
                function: functions_1.uniquenessTags,
            },
        },
        'asyncapi-tags': {
            description: 'AsyncAPI object must have non-empty "tags" array.',
            recommended: true,
            formats: [spectral_formats_1.aas2],
            given: '$',
            then: {
                field: 'tags',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-3-tags': {
            description: 'AsyncAPI document must have non-empty "tags" array.',
            recommended: true,
            formats: [spectral_formats_1.aas3],
            given: ['$.info'],
            then: {
                field: 'tags',
                function: spectral_functions_1.truthy,
            },
        },
        'asyncapi-unused-components-schema': {
            description: 'Potentially unused components schema has been detected.',
            recommended: true,
            resolved: false,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: '$.components.schemas',
            then: {
                function: spectral_functions_1.unreferencedReusableObject,
                functionOptions: {
                    reusableObjectsLocation: '#/components/schemas',
                },
            },
        },
        'asyncapi-unused-components-server': {
            description: 'Potentially unused components server has been detected.',
            recommended: true,
            resolved: false,
            formats: [spectral_formats_1.aas2, spectral_formats_1.aas3],
            given: '$.components.servers',
            then: {
                function: spectral_functions_1.unreferencedReusableObject,
                functionOptions: {
                    reusableObjectsLocation: '#/components/servers',
                },
            },
        },
        'asyncapi-3-document-resolved': {
            description: 'Checking if the AsyncAPI v3 document has valid structure after resolving references.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            given: '$',
            formats: [spectral_formats_1.aas3],
            then: {
                function: asyncApiDocumentSchema_1.asyncApiDocumentSchema,
                functionOptions: {
                    resolved: true,
                },
            },
        },
        'asyncapi-3-document-unresolved': {
            description: 'Checking if the AsyncAPI v3 document has valid structure before resolving references.',
            message: '{{error}}',
            severity: 'error',
            recommended: true,
            resolved: false,
            given: '$',
            formats: [spectral_formats_1.aas3],
            then: {
                function: asyncApiDocumentSchema_1.asyncApiDocumentSchema,
                functionOptions: {
                    resolved: false,
                },
            },
        },
    },
};
//# sourceMappingURL=index.js.map
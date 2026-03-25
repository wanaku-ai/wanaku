"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const spectral_functions_1 = require("@stoplight/spectral-functions");
const arazzoDocumentSchema_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoDocumentSchema"));
const arazzoWorkflowIdUniqueness_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoWorkflowIdUniqueness"));
const arazzoStepIdUniqueness_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepIdUniqueness"));
const arazzoWorkflowOutputNamesValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoWorkflowOutputNamesValidation"));
const arazzoStepOutputNamesValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepOutputNamesValidation"));
const arazzoStepParametersValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepParametersValidation"));
const arazzoStepFailureActionsValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepFailureActionsValidation"));
const arazzoStepSuccessActionsValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepSuccessActionsValidation"));
const arazzoWorkflowDependsOnValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoWorkflowDependsOnValidation"));
const arazzoStepSuccessCriteriaValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepSuccessCriteriaValidation"));
const arazzoStepRequestBodyValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepRequestBodyValidation"));
const arazzoStepValidation_1 = (0, tslib_1.__importDefault)(require("./functions/arazzoStepValidation"));
exports.default = {
    documentationUrl: 'https://meta.stoplight.io/docs/spectral/docs/reference/arazzo-rules.md',
    formats: [spectral_formats_1.arazzo1_0],
    rules: {
        'arazzo-document-schema': {
            description: 'Arazzo Document must be valid against the Arazzo schema.',
            message: '{{error}}',
            severity: 0,
            given: '$',
            then: {
                function: arazzoDocumentSchema_1.default,
            },
        },
        'arazzo-workflowId-unique': {
            description: 'Every workflow must have unique "workflowId".',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoWorkflowIdUniqueness_1.default,
            },
        },
        'arazzo-workflow-output-validation': {
            description: 'Every workflow output must have unique name and its value must be a valid runtime expression.',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoWorkflowOutputNamesValidation_1.default,
            },
        },
        'arazzo-workflow-stepId-unique': {
            description: 'Every step must have unique "stepId".',
            message: `{{error}}`,
            severity: 0,
            given: '$.workflows[*]',
            then: {
                function: arazzoStepIdUniqueness_1.default,
            },
        },
        'arazzo-step-output-validation': {
            description: 'Every step output must have unique name and its value must be a valid runtime expression.',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepOutputNamesValidation_1.default,
            },
        },
        'arazzo-step-parameters-validation': {
            description: 'Step parameters and workflow parameters must valid.',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepParametersValidation_1.default,
            },
        },
        'arazzo-step-failure-actions-validation': {
            description: 'Every failure action must have a unique "name", and the fields "workflowId" and "stepId" are mutually exclusive.',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepFailureActionsValidation_1.default,
            },
        },
        'arazzo-step-success-actions-validation': {
            description: 'Every success action must have a unique "name", and the fields "workflowId" and "stepId" are mutually exclusive.',
            message: `{{error}}`,
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepSuccessActionsValidation_1.default,
            },
        },
        'arazzo-workflow-depends-on-validation': {
            description: 'Every workflow dependency must be valid.',
            severity: 0,
            given: '$',
            then: {
                function: arazzoWorkflowDependsOnValidation_1.default,
            },
        },
        'arazzo-step-success-criteria-validation': {
            description: 'Every success criteria must have a valid context, conditions, and types.',
            message: `{{error}}`,
            severity: 0,
            given: '$.workflows[*]',
            then: {
                function: arazzoStepSuccessCriteriaValidation_1.default,
            },
        },
        'arazzo-step-request-body-validation': {
            description: 'Every step request body must have a valid `contentType` and use of runtime expressions.',
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepRequestBodyValidation_1.default,
            },
        },
        'arazzo-step-validation': {
            description: 'Every step must have a valid "stepId" and an valid "operationId" or "operationPath" or "workflowId".',
            severity: 0,
            given: '$',
            then: {
                function: arazzoStepValidation_1.default,
            },
        },
        'arazzo-no-script-tags-in-markdown': {
            description: 'Markdown descriptions must not have "<script>" tags.',
            given: '$..[description,title]',
            then: {
                function: spectral_functions_1.pattern,
                functionOptions: {
                    notMatch: '<script',
                },
            },
        },
        'arazzo-info-description': {
            description: 'Info "description" should be present and non-empty string.',
            severity: 'warn',
            given: '$',
            then: {
                field: 'info.description',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-info-summary': {
            description: 'Info "summary" is recommended be present and be a non-empty string.',
            severity: 'hint',
            given: '$',
            then: {
                field: 'info.summary',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-source-descriptions-type': {
            description: 'Source Description "type" should be present.',
            severity: 'warn',
            given: '$.sourceDescriptions[*]',
            then: {
                field: 'type',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-workflow-workflowId': {
            description: 'Workflow "workflowId" should follow the pattern "^[A-Za-z0-9_\\-]+$".',
            severity: 'warn',
            given: '$.workflows[*]',
            then: {
                field: 'workflowId',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    match: '^[A-Za-z0-9_\\-]+$',
                },
            },
        },
        'arazzo-workflow-description': {
            description: 'Workflow "description" should be present and non-empty string.',
            severity: 'warn',
            given: '$.workflows[*]',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-workflow-summary': {
            description: 'Workflow "summary" should be present and non-empty string.',
            severity: 'hint',
            given: '$.workflows[*]',
            then: {
                field: 'summary',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-step-description': {
            description: 'Step "description" should be present and non-empty string.',
            severity: 'warn',
            given: '$.workflows[*].steps[*]',
            then: {
                field: 'description',
                function: spectral_functions_1.truthy,
            },
        },
        'arazzo-step-stepId': {
            description: 'Step "stepId" should follow the pattern "^[A-Za-z0-9_\\-]+$".',
            severity: 'warn',
            given: '$.workflows[*].steps[*]',
            then: {
                field: 'stepId',
                function: spectral_functions_1.pattern,
                functionOptions: {
                    match: '^[A-Za-z0-9_\\-]+$',
                },
            },
        },
        'arazzo-step-operationPath': {
            description: 'It is recommended to use "operationId" rather than "operationPath".',
            severity: 'hint',
            given: '$.workflows[*].steps[*]',
            then: {
                field: 'operationPath',
                function: spectral_functions_1.falsy,
            },
        },
    },
};
//# sourceMappingURL=index.js.map
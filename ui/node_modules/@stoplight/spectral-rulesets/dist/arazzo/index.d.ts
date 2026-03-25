import arazzoWorkflowIdUniqueness from './functions/arazzoWorkflowIdUniqueness';
import arazzoStepParametersValidation from './functions/arazzoStepParametersValidation';
import arazzoStepFailureActionsValidation from './functions/arazzoStepFailureActionsValidation';
import arazzoStepSuccessActionsValidation from './functions/arazzoStepSuccessActionsValidation';
import arazzoWorkflowDependsOnValidation from './functions/arazzoWorkflowDependsOnValidation';
import arazzoStepSuccessCriteriaValidation from './functions/arazzoStepSuccessCriteriaValidation';
import arazzoStepRequestBodyValidation from './functions/arazzoStepRequestBodyValidation';
import arazzoStepValidation from './functions/arazzoStepValidation';
declare const _default: {
    documentationUrl: string;
    formats: import("@stoplight/spectral-core").Format<void>[];
    rules: {
        'arazzo-document-schema': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-workflowId-unique': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoWorkflowIdUniqueness;
            };
        };
        'arazzo-workflow-output-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<import("./functions/types/arazzoTypes").ArazzoSpecification, null>;
            };
        };
        'arazzo-workflow-stepId-unique': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<{
                    steps: {
                        stepId?: string | undefined;
                    }[];
                }, null>;
            };
        };
        'arazzo-step-output-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<import("./functions/types/arazzoTypes").ArazzoSpecification, null>;
            };
        };
        'arazzo-step-parameters-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepParametersValidation;
            };
        };
        'arazzo-step-failure-actions-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepFailureActionsValidation;
            };
        };
        'arazzo-step-success-actions-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepSuccessActionsValidation;
            };
        };
        'arazzo-workflow-depends-on-validation': {
            description: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoWorkflowDependsOnValidation;
            };
        };
        'arazzo-step-success-criteria-validation': {
            description: string;
            message: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepSuccessCriteriaValidation;
            };
        };
        'arazzo-step-request-body-validation': {
            description: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepRequestBodyValidation;
            };
        };
        'arazzo-step-validation': {
            description: string;
            severity: number;
            given: string;
            then: {
                function: typeof arazzoStepValidation;
            };
        };
        'arazzo-no-script-tags-in-markdown': {
            description: string;
            given: string;
            then: {
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    notMatch: string;
                };
            };
        };
        'arazzo-info-description': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-info-summary': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-source-descriptions-type': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-workflow-workflowId': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    match: string;
                };
            };
        };
        'arazzo-workflow-description': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-workflow-summary': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-step-description': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
        'arazzo-step-stepId': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<string, import("@stoplight/spectral-functions").PatternOptions>;
                functionOptions: {
                    match: string;
                };
            };
        };
        'arazzo-step-operationPath': {
            description: string;
            severity: string;
            given: string;
            then: {
                field: string;
                function: import("@stoplight/spectral-core").RulesetFunctionWithValidator<unknown, null>;
            };
        };
    };
};
export default _default;

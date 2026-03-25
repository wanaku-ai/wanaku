export declare type CriterionExpressionType = {
    type: 'jsonpath' | 'xpath';
    version: 'draft-goessner-dispatch-jsonpath-00' | 'xpath-30' | 'xpath-20' | 'xpath-10';
};
export declare type Criterion = {
    context?: string;
    condition: string;
    type?: 'simple' | 'regex' | 'jsonpath' | 'xpath' | CriterionExpressionType;
};
export declare type Parameter = {
    name: string;
    in?: string;
    value?: unknown;
};
export declare type FailureAction = {
    name: string;
    type: string;
    workflowId?: string;
    stepId?: string;
    retryAfter?: number;
    retryLimit?: number;
    criteria?: Criterion[];
};
export declare type SuccessAction = {
    name: string;
    type: string;
    workflowId?: string;
    stepId?: string;
    criteria?: Criterion[];
};
export declare type ReusableObject = {
    reference: string;
    value?: unknown;
};
export declare type PayloadReplacement = {
    target: string;
    value: unknown | string;
};
export declare type RequestBody = {
    contentType?: string;
    payload?: unknown | string;
    replacements?: PayloadReplacement[];
};
export declare type Step = {
    stepId: string;
    onFailure?: (FailureAction | ReusableObject)[];
    onSuccess?: (SuccessAction | ReusableObject)[];
    parameters?: (Parameter | ReusableObject)[];
    successCriteria?: Criterion[];
    requestBody?: RequestBody;
    outputs?: {
        [key: string]: string;
    };
    workflowId?: string;
    operationId?: string;
    operationPath?: string;
};
export declare type SourceDescription = {
    name: string;
    url: string;
    type?: 'arazzo' | 'openapi';
};
export declare type Workflow = {
    workflowId: string;
    steps: Step[];
    inputs?: Record<string, unknown>;
    parameters?: (Parameter | ReusableObject)[];
    successActions?: (SuccessAction | ReusableObject)[];
    failureActions?: (FailureAction | ReusableObject)[];
    dependsOn?: string[];
    outputs?: {
        [key: string]: string;
    };
};
export declare type ArazzoSpecification = {
    workflows: Workflow[];
    sourceDescriptions?: SourceDescription[];
    components?: {
        inputs?: Record<string, unknown>;
        parameters?: Record<string, Parameter>;
        successActions?: Record<string, SuccessAction>;
        failureActions?: Record<string, FailureAction>;
        [key: string]: unknown;
    };
};

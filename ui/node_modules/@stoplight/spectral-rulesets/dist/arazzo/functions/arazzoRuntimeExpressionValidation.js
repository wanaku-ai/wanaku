"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
function isNonNullObject(value) {
    return value !== null && typeof value === 'object';
}
function validateReusableParameterExpression(expression, arazzoSpec) {
    var _a;
    const parametersRegex = /^\$components\.parameters\.([A-Za-z0-9_\\-]+)$/;
    const match = parametersRegex.exec(expression);
    if (!match) {
        return false;
    }
    const [, paramName] = match;
    if (((_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.parameters) && paramName in arazzoSpec.components.parameters) {
        return true;
    }
    return false;
}
function validateStepsExpression(stepsExpression, arazzoSpec, currentWorkflowIndex) {
    var _a;
    const stepsRegex = /^\$steps\.([A-Za-z0-9_\\-]+)\.(.*)$/;
    const match = stepsRegex.exec(stepsExpression);
    if (!match) {
        return false;
    }
    const [, stepId] = match;
    if (arazzoSpec == null || !Array.isArray(arazzoSpec.workflows) || arazzoSpec.workflows.length === 0) {
        return false;
    }
    let stepsToSearch = [];
    if (currentWorkflowIndex !== undefined &&
        currentWorkflowIndex >= 0 &&
        arazzoSpec.workflows[currentWorkflowIndex] != null) {
        stepsToSearch = (_a = arazzoSpec.workflows[currentWorkflowIndex].steps) !== null && _a !== void 0 ? _a : [];
    }
    else {
        stepsToSearch = arazzoSpec.workflows.flatMap(workflow => { var _a; return (_a = workflow.steps) !== null && _a !== void 0 ? _a : []; });
    }
    if (stepsToSearch == null || stepsToSearch.length === 0) {
        return false;
    }
    const step = stepsToSearch.find(step => step.stepId === stepId);
    if (!step) {
        return false;
    }
    return true;
}
function validateWorkflowsExpression(workflowsExpression, arazzoSpec) {
    const workflowsRegex = /^\$workflows\.([A-Za-z0-9_\\-]+)\.(.*)$/;
    const match = workflowsRegex.exec(workflowsExpression);
    if (!match) {
        return false;
    }
    const [, workflowId, remainingPath] = match;
    if (arazzoSpec == null || !Array.isArray(arazzoSpec.workflows) || arazzoSpec.workflows.length === 0) {
        return false;
    }
    const workflowIndex = arazzoSpec.workflows.findIndex(workflow => workflow.workflowId === workflowId);
    if (workflowIndex === -1) {
        return false;
    }
    if (remainingPath.startsWith('steps.')) {
        return validateStepsExpression(`$steps.${remainingPath.slice(6)}`, arazzoSpec, workflowIndex);
    }
    return true;
}
function validateInputsExpression(inputsExpression, arazzoSpec, currentWorkflowIndex) {
    var _a;
    const inputsRegex = /^\$inputs\.([A-Za-z0-9_\\-]+)$/;
    const match = inputsRegex.exec(inputsExpression);
    if (!match) {
        return false;
    }
    const [, inputName] = match;
    if (arazzoSpec == null ||
        !Array.isArray(arazzoSpec.workflows) ||
        arazzoSpec.workflows.length === 0 ||
        currentWorkflowIndex === undefined) {
        return false;
    }
    const currentWorkflow = arazzoSpec.workflows[currentWorkflowIndex];
    if (!currentWorkflow.inputs) {
        return false;
    }
    if ('properties' in currentWorkflow.inputs) {
        const properties = currentWorkflow.inputs.properties;
        return properties ? inputName in properties : false;
    }
    if ('$ref' in currentWorkflow.inputs) {
        const refPath = currentWorkflow.inputs.$ref.replace(/^#\//, '').split('/');
        let refObject = arazzoSpec;
        for (const part of refPath) {
            if (isNonNullObject(refObject) && part in refObject) {
                refObject = refObject[part];
            }
            else {
                return false;
            }
        }
        const properties = (_a = refObject) === null || _a === void 0 ? void 0 : _a.properties;
        return properties ? inputName in properties : false;
    }
    return false;
}
function validateReusableSuccessActionExpression(expression, arazzoSpec) {
    var _a;
    const successActionsRegex = /^\$components\.successActions\.([A-Za-z0-9_\\-]+)$/;
    const match = successActionsRegex.exec(expression);
    if (!match) {
        return false;
    }
    const [, actionName] = match;
    if (((_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.successActions) && actionName in arazzoSpec.components.successActions) {
        return true;
    }
    return false;
}
function validateReusableFailureActionExpression(expression, arazzoSpec) {
    var _a;
    const failureActionsRegex = /^\$components\.failureActions\.([A-Za-z0-9_\\-]+)$/;
    const match = failureActionsRegex.exec(expression);
    if (!match) {
        return false;
    }
    const [, actionName] = match;
    if (((_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.failureActions) && actionName in arazzoSpec.components.failureActions) {
        return true;
    }
    return false;
}
function arazzoRuntimeExpressionValidation(expression, arazzoSpec, currentWorkflowIndex) {
    if (!expression && !arazzoSpec) {
        return false;
    }
    const validPrefixes = [
        '$url',
        '$method',
        '$statusCode',
        '$request.',
        '$response.',
        '$message.',
        '$inputs.',
        '$outputs.',
        '$steps.',
        '$workflows.',
        '$sourceDescriptions.',
        '$components.inputs.',
        '$components.parameters.',
        '$components.successActions.',
        '$components.failureActions.',
    ];
    const isValidPrefix = validPrefixes.some(prefix => expression.startsWith(prefix));
    if (!isValidPrefix) {
        return false;
    }
    if (expression.startsWith('$steps.') && arazzoSpec) {
        return validateStepsExpression(expression, arazzoSpec, currentWorkflowIndex);
    }
    if (expression.startsWith('$workflows.') && arazzoSpec) {
        return validateWorkflowsExpression(expression, arazzoSpec);
    }
    if (expression.startsWith('$inputs.') && arazzoSpec) {
        return validateInputsExpression(expression, arazzoSpec, currentWorkflowIndex);
    }
    if (expression.startsWith('$components.failureActions.') && arazzoSpec) {
        return validateReusableFailureActionExpression(expression, arazzoSpec);
    }
    if (expression.startsWith('$components.successActions.') && arazzoSpec) {
        return validateReusableSuccessActionExpression(expression, arazzoSpec);
    }
    if (expression.startsWith('$components.parameters.') && arazzoSpec) {
        return validateReusableParameterExpression(expression, arazzoSpec);
    }
    return true;
}
exports.default = arazzoRuntimeExpressionValidation;
//# sourceMappingURL=arazzoRuntimeExpressionValidation.js.map
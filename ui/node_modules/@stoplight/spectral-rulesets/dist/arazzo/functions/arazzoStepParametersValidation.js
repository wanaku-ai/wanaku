"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const getAllParameters_1 = (0, tslib_1.__importDefault)(require("./utils/getAllParameters"));
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
function arazzoStepParametersValidation(target, _options) {
    const results = [];
    if (Array.isArray(target.workflows)) {
        target.workflows.forEach((workflow, workflowIndex) => {
            workflow.steps.forEach((step, stepIndex) => {
                if (!step.parameters)
                    return;
                const { workflowId, operationId, operationPath } = step;
                const stepParams = (0, getAllParameters_1.default)(step, workflow, target);
                if (Array.isArray(stepParams)) {
                    const seenNames = new Set();
                    stepParams.forEach((param, paramIndex) => {
                        const originalName = param.name
                            .replace('masked-invalid-reusable-parameter-reference-', '')
                            .replace('masked-unresolved-parameter-reference-', '')
                            .replace('masked-duplicate-', '');
                        if (seenNames.has(originalName)) {
                            results.push({
                                message: `"${originalName}" must be unique within the combined parameters.`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                        else {
                            seenNames.add(originalName);
                        }
                        if (param.name.startsWith('masked-invalid-reusable-parameter-reference-')) {
                            results.push({
                                message: `Invalid runtime expression for reusable parameter reference: "${originalName}".`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                        if (param.name.startsWith('masked-unresolved-parameter-reference-')) {
                            results.push({
                                message: `Unresolved reusable parameter reference: "${originalName}".`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                        if (param.name.startsWith('masked-duplicate-')) {
                            results.push({
                                message: `Duplicate parameter: "${originalName}" must be unique within the combined parameters.`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                    });
                }
                const hasInField = stepParams.some(param => 'in' in param && param.in !== undefined);
                const noInField = stepParams.some(param => !('in' in param) || param.in === undefined);
                if (hasInField && noInField) {
                    results.push({
                        message: `Parameters must not mix "in" field presence.`,
                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters'],
                    });
                }
                if (workflowId != null && hasInField) {
                    results.push({
                        message: `Step with "workflowId" must not have parameters with an "in" field.`,
                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters'],
                    });
                }
                if ((operationId != null || operationPath != null) && noInField) {
                    results.push({
                        message: `Step with "operationId" or "operationPath" must have parameters with an "in" field.`,
                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters'],
                    });
                }
                stepParams.forEach((param, paramIndex) => {
                    if (typeof param.value === 'string' && param.value.startsWith('$')) {
                        const validPatterns = [
                            /^\$inputs\./,
                            /^\$steps\.[A-Za-z0-9_-]+\./,
                            /^\$workflows\.[A-Za-z0-9_-]+\.steps\.[A-Za-z0-9_-]+\./,
                        ];
                        const isValidPattern = validPatterns.some(pattern => pattern.test(param.value));
                        if (!isValidPattern) {
                            results.push({
                                message: `Invalid runtime expression: "${param.value}" for parameter.`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                        else if (!(0, arazzoRuntimeExpressionValidation_1.default)(param.value, target, workflowIndex)) {
                            results.push({
                                message: `Invalid runtime expression: "${param.value}" for parameter.`,
                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'parameters', paramIndex],
                            });
                        }
                    }
                });
            });
        });
    }
    return results;
}
exports.default = arazzoStepParametersValidation;
//# sourceMappingURL=arazzoStepParametersValidation.js.map
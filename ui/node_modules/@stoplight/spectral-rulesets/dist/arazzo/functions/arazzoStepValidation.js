"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
const OPERATION_PATH_REGEX = /^\{\$sourceDescriptions\.[a-zA-Z0-9_-]+\.(url)\}#.+$/;
function arazzoStepValidation(targetVal, _options) {
    const results = [];
    if (!Array.isArray(targetVal.sourceDescriptions) || targetVal.sourceDescriptions.length === 0) {
        results.push({
            message: 'sourceDescriptions is missing in the Arazzo Specification.',
            path: ['sourceDescriptions'],
        });
        return results;
    }
    const sourceDescriptionNames = new Set(targetVal.sourceDescriptions.map(sd => sd.name));
    targetVal.workflows.forEach((workflow, workflowIndex) => {
        if (!Array.isArray(workflow.steps)) {
            return;
        }
        workflow.steps.forEach((step, stepIndex) => {
            const { operationId, operationPath, workflowId } = step;
            if (operationId != null) {
                if (operationId.startsWith('$')) {
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(operationId, targetVal)) {
                        results.push({
                            message: `Runtime expression "${operationId}" is invalid in step "${step.stepId}".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'operationId'],
                        });
                    }
                    const parts = operationId.split('.');
                    const sourceName = parts[1];
                    if (!sourceDescriptionNames.has(sourceName)) {
                        results.push({
                            message: `Source description "${sourceName}" not found for operationId "${operationId}" in step "${step.stepId}".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'operationId'],
                        });
                    }
                }
            }
            if (operationPath != null) {
                if (!OPERATION_PATH_REGEX.test(operationPath)) {
                    results.push({
                        message: `OperationPath "${operationPath}" must be a valid runtime expression following the format "{$sourceDescriptions.<name>.url}#<json-pointer>".`,
                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'operationPath'],
                    });
                }
                else {
                    const sourceName = operationPath.split('.')[1];
                    if (!sourceDescriptionNames.has(sourceName)) {
                        results.push({
                            message: `Source description "${sourceName}" not found for operationPath "${operationPath}" in step "${step.stepId}".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'operationPath'],
                        });
                    }
                }
            }
            if (workflowId != null) {
                if (workflowId.startsWith('$')) {
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(workflowId)) {
                        results.push({
                            message: `Runtime expression "${workflowId}" is invalid in step "${step.stepId}".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'workflowId'],
                        });
                    }
                    const parts = workflowId.split('.');
                    const sourceName = parts[1];
                    if (!sourceDescriptionNames.has(sourceName)) {
                        results.push({
                            message: `Source description "${sourceName}" not found for workflowId "${workflowId}" in step "${step.stepId}".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'workflowId'],
                        });
                    }
                }
            }
        });
    });
    return results;
}
exports.default = arazzoStepValidation;
//# sourceMappingURL=arazzoStepValidation.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const getAllSuccessActions_1 = (0, tslib_1.__importDefault)(require("./utils/getAllSuccessActions"));
const arazzoCriterionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoCriterionValidation"));
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
function arazzoStepSuccessActionsValidation(target, _options) {
    const results = [];
    if (Array.isArray(target.workflows)) {
        target.workflows.forEach((workflow, workflowIndex) => {
            if (Array.isArray(workflow.steps)) {
                workflow.steps.forEach((step, stepIndex) => {
                    const resolvedActions = (0, getAllSuccessActions_1.default)(step, workflow, target);
                    if (Array.isArray(resolvedActions)) {
                        const seenNames = new Set();
                        resolvedActions.forEach((action, actionIndex) => {
                            var _a, _b;
                            const originalName = action.name.replace(/^(masked-(invalid-reusable-success-action-reference-|non-existing-success-action-reference-|duplicate-))/, '');
                            if (seenNames.has(originalName)) {
                                results.push({
                                    message: `"${originalName}" must be unique within the combined success actions.`,
                                    path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                });
                            }
                            else {
                                seenNames.add(originalName);
                            }
                            if (action.name.startsWith('masked-invalid-reusable-success-action-reference-')) {
                                results.push({
                                    message: `Invalid runtime expression for reusable action reference: "${originalName}".`,
                                    path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                });
                            }
                            if (action.name.startsWith('masked-non-existing-success-action-reference-')) {
                                results.push({
                                    message: `Non-existing reusable action reference: "${originalName}".`,
                                    path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                });
                            }
                            if (action.name.startsWith('masked-duplicate-')) {
                                results.push({
                                    message: `Duplicate success action name: "${originalName}".`,
                                    path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                });
                            }
                            if (action.type === 'goto') {
                                if (action.workflowId != null) {
                                    if (action.workflowId.startsWith('$')) {
                                        if (!(0, arazzoRuntimeExpressionValidation_1.default)(action.workflowId, target) ||
                                            !((_b = (_a = target.sourceDescriptions) === null || _a === void 0 ? void 0 : _a.some(desc => { var _a; return desc.name === ((_a = action.workflowId) !== null && _a !== void 0 ? _a : '').split('.')[1]; })) !== null && _b !== void 0 ? _b : false)) {
                                            results.push({
                                                message: `"workflowId" "${action.workflowId}" is not a valid reference or does not exist in sourceDescriptions.`,
                                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                            });
                                        }
                                    }
                                    else {
                                        if (!target.workflows.some(wf => wf.workflowId === action.workflowId)) {
                                            results.push({
                                                message: `"workflowId" "${action.workflowId}" does not exist within the local Arazzo Document workflows.`,
                                                path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                            });
                                        }
                                    }
                                }
                                if (action.stepId != null) {
                                    if (!workflow.steps.some(s => s.stepId === action.stepId)) {
                                        results.push({
                                            message: `"stepId" "${action.stepId}" does not exist within the current workflow.`,
                                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                        });
                                    }
                                }
                                if (action.workflowId != null && action.stepId != null) {
                                    results.push({
                                        message: `"workflowId" and "stepId" are mutually exclusive and cannot be specified together.`,
                                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'onSuccess', actionIndex],
                                    });
                                }
                            }
                            if (Array.isArray(action.criteria)) {
                                action.criteria.forEach((criterion, criterionIndex) => {
                                    const criterionResults = (0, arazzoCriterionValidation_1.default)(criterion, [
                                        'workflows',
                                        workflowIndex,
                                        'steps',
                                        stepIndex,
                                        'onSuccess',
                                        actionIndex,
                                        'criteria',
                                        criterionIndex,
                                    ], target);
                                    results.push(...criterionResults);
                                });
                            }
                        });
                    }
                });
            }
        });
    }
    return results;
}
exports.default = arazzoStepSuccessActionsValidation;
//# sourceMappingURL=arazzoStepSuccessActionsValidation.js.map
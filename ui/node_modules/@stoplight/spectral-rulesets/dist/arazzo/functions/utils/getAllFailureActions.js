"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const json_1 = require("@stoplight/json");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("../arazzoRuntimeExpressionValidation"));
function isFailureAction(action) {
    return typeof action === 'object' && action !== null && 'name' in action && 'type' in action;
}
function processReusableAction(action, arazzoSpec) {
    var _a, _b;
    const actionName = action.reference;
    if (!action.reference.startsWith('$components.failureActions.')) {
        return { name: `masked-invalid-reusable-failure-action-reference-${actionName}`, type: '' };
    }
    if (!(0, arazzoRuntimeExpressionValidation_1.default)(action.reference, arazzoSpec)) {
        return { name: `masked-invalid-reusable-failure-action-reference-${actionName}`, type: '' };
    }
    const refPath = action.reference.replace('$components.failureActions.', '');
    const resolvedAction = (_b = (_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.failureActions) === null || _b === void 0 ? void 0 : _b[refPath];
    if (!resolvedAction) {
        return { name: `masked-unresolved-failure-action-reference-${actionName}`, type: '' };
    }
    return resolvedAction;
}
function getAllFailureActions(step, workflow, arazzoSpec) {
    const resolvedFailureActions = [];
    const resolvedStepFailureActions = [];
    const resolveActions = (actions, targetArray) => {
        actions.forEach(action => {
            let actionToPush;
            if ((0, json_1.isPlainObject)(action) && 'reference' in action) {
                actionToPush = processReusableAction(action, arazzoSpec);
            }
            else {
                actionToPush = action;
            }
            if (isFailureAction(actionToPush)) {
                const isDuplicate = targetArray.some(existingAction => existingAction.name === actionToPush.name);
                if (isDuplicate) {
                    actionToPush = {
                        ...actionToPush,
                        name: `masked-duplicate-${actionToPush.name}`,
                    };
                }
                targetArray.push(actionToPush);
            }
        });
    };
    if (workflow.failureActions) {
        resolveActions(workflow.failureActions, resolvedFailureActions);
    }
    if (step.onFailure) {
        resolveActions(step.onFailure, resolvedStepFailureActions);
    }
    resolvedStepFailureActions.forEach(action => {
        const existingActionIndex = resolvedFailureActions.findIndex(a => a.name === action.name);
        if (existingActionIndex !== -1) {
            resolvedFailureActions[existingActionIndex] = action;
        }
        else {
            resolvedFailureActions.push(action);
        }
    });
    return resolvedFailureActions;
}
exports.default = getAllFailureActions;
//# sourceMappingURL=getAllFailureActions.js.map
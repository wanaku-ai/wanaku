"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const json_1 = require("@stoplight/json");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("../arazzoRuntimeExpressionValidation"));
const resolveReusableSuccessActions = (reusableObject, arazzoSpec) => {
    var _a, _b;
    const refPath = reusableObject.reference.replace('$components.successActions.', '');
    return (_b = (_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.successActions) === null || _b === void 0 ? void 0 : _b[refPath];
};
function isSuccessAction(action) {
    return typeof action === 'object' && action !== null && 'name' in action && 'type' in action;
}
function getAllSuccessActions(step, workflow, arazzoSpec) {
    const resolvedSuccessActions = [];
    const resolvedStepSuccessActions = [];
    const processReusableAction = (action) => {
        const actionName = action.reference;
        if (!(0, arazzoRuntimeExpressionValidation_1.default)(action.reference, arazzoSpec)) {
            return { name: `masked-invalid-reusable-success-action-reference-${actionName}`, type: '' };
        }
        const resolvedAction = resolveReusableSuccessActions(action, arazzoSpec);
        if (!resolvedAction) {
            return { name: `masked-non-existing-success-action-reference-${actionName}`, type: '' };
        }
        return resolvedAction;
    };
    const resolveActions = (actions, targetArray) => {
        actions.forEach(action => {
            let actionToPush;
            if ((0, json_1.isPlainObject)(action) && 'reference' in action) {
                actionToPush = processReusableAction(action);
            }
            else {
                actionToPush = action;
            }
            if (isSuccessAction(actionToPush)) {
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
    if (workflow.successActions) {
        resolveActions(workflow.successActions, resolvedSuccessActions);
    }
    if (step.onSuccess) {
        resolveActions(step.onSuccess, resolvedStepSuccessActions);
    }
    resolvedStepSuccessActions.forEach(action => {
        const existingActionIndex = resolvedSuccessActions.findIndex(a => a.name === action.name);
        if (existingActionIndex !== -1) {
            resolvedSuccessActions[existingActionIndex] = action;
        }
        else {
            resolvedSuccessActions.push(action);
        }
    });
    return resolvedSuccessActions;
}
exports.default = getAllSuccessActions;
//# sourceMappingURL=getAllSuccessActions.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const arazzoCriterionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoCriterionValidation"));
function arazzoStepSuccessCriteriaValidation(targetVal, _options) {
    const results = [];
    if (Array.isArray(targetVal.workflows)) {
        targetVal.workflows.forEach((workflow, workflowIndex) => {
            if (Array.isArray(workflow.steps)) {
                workflow.steps.forEach((step, stepIndex) => {
                    if (Array.isArray(step.successCriteria)) {
                        step.successCriteria.forEach((criterion, criterionIndex) => {
                            const criterionResults = (0, arazzoCriterionValidation_1.default)(criterion, ['workflows', workflowIndex, 'steps', stepIndex, 'successCriteria', criterionIndex], targetVal);
                            results.push(...criterionResults);
                        });
                    }
                });
            }
        });
    }
    return results;
}
exports.default = arazzoStepSuccessCriteriaValidation;
//# sourceMappingURL=arazzoStepSuccessCriteriaValidation.js.map
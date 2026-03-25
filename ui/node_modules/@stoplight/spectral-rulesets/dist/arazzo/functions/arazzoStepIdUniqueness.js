"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            steps: {
                type: 'array',
                items: {
                    type: 'object',
                    properties: {
                        stepId: {
                            type: 'string',
                        },
                    },
                },
            },
        },
    },
    options: null,
}, function arazzoStepIdUniqueness(targetVal, _opts) {
    const results = [];
    const stepIds = new Set();
    if (!Array.isArray(targetVal.steps)) {
        return results;
    }
    targetVal.steps.forEach((step, index) => {
        const { stepId } = step;
        if (stepId == null) {
            results.push({
                message: `Step at index ${index} is missing a "stepId". Each step should have a unique "stepId".`,
                path: ['steps', index],
            });
            return;
        }
        if (stepIds.has(stepId)) {
            results.push({
                message: `"stepId" must be unique within the workflow.`,
                path: ['steps', index, 'stepId'],
            });
        }
        else {
            stepIds.add(stepId);
        }
    });
    return results;
});
//# sourceMappingURL=arazzoStepIdUniqueness.js.map
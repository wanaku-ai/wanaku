"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const spectral_core_1 = require("@stoplight/spectral-core");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
const OUTPUT_NAME_PATTERN = /^[a-zA-Z0-9.\-_]+$/;
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            workflows: {
                type: 'array',
                items: {
                    type: 'object',
                    properties: {
                        steps: {
                            type: 'array',
                            items: {
                                type: 'object',
                                properties: {
                                    outputs: {
                                        type: 'object',
                                        additionalProperties: { type: 'string' },
                                    },
                                },
                            },
                        },
                    },
                },
            },
        },
    },
    options: null,
}, function arazzoStepOutputNamesValidation(targetVal, _opts) {
    const results = [];
    if (!Array.isArray(targetVal.workflows)) {
        return results;
    }
    targetVal.workflows.forEach((workflow, workflowIndex) => {
        workflow.steps.forEach((step, stepIndex) => {
            if (step.outputs && typeof step.outputs === 'object') {
                const seenOutputNames = new Set();
                Object.entries(step.outputs).forEach(([outputName, outputValue], outputIndex) => {
                    if (!OUTPUT_NAME_PATTERN.test(outputName)) {
                        results.push({
                            message: `"${outputName}" does not match the required pattern "^[a-zA-Z0-9.\\-_]+$".`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                    if (seenOutputNames.has(outputName)) {
                        results.push({
                            message: `"${outputName}" must be unique within the step outputs.`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                    else {
                        seenOutputNames.add(outputName);
                    }
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(outputValue, targetVal, workflowIndex)) {
                        results.push({
                            message: `"${outputValue}" is not a valid runtime expression.`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                });
            }
        });
    });
    return results;
});
//# sourceMappingURL=arazzoStepOutputNamesValidation.js.map
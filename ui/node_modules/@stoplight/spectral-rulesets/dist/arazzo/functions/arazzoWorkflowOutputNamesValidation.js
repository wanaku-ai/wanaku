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
                        outputs: {
                            type: 'object',
                            additionalProperties: { type: 'string' },
                        },
                    },
                },
            },
        },
    },
    options: null,
}, function arazzoWorkflowOutputNamesValidation(targetVal, _opts) {
    const results = [];
    if (Array.isArray(targetVal.workflows)) {
        targetVal.workflows.forEach((workflow, workflowIndex) => {
            if (workflow.outputs && typeof workflow.outputs === 'object') {
                const seenOutputNames = new Set();
                Object.entries(workflow.outputs).forEach(([outputName, outputValue], outputIndex) => {
                    if (!OUTPUT_NAME_PATTERN.test(outputName)) {
                        results.push({
                            message: `"${outputName}" does not match the required pattern "^[a-zA-Z0-9.\\-_]+$".`,
                            path: ['workflows', workflowIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                    if (seenOutputNames.has(outputName)) {
                        results.push({
                            message: `"${outputName}" must be unique within the workflow outputs.`,
                            path: ['workflows', workflowIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                    else {
                        seenOutputNames.add(outputName);
                    }
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(outputValue, targetVal, workflowIndex)) {
                        results.push({
                            message: `"${outputValue}" is not a valid runtime expression.`,
                            path: ['workflows', workflowIndex, 'outputs', outputName, outputIndex],
                        });
                    }
                });
            }
        });
    }
    return results;
});
//# sourceMappingURL=arazzoWorkflowOutputNamesValidation.js.map
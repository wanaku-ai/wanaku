"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.arazzoStepRequestBodyValidation = void 0;
const tslib_1 = require("tslib");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
const MIME_TYPE_REGEX = /^(application|audio|font|example|image|message|model|multipart|text|video)\/[a-zA-Z0-9!#$&^_.+-]{1,127}$/;
function arazzoStepRequestBodyValidation(target, _options) {
    const results = [];
    if (Array.isArray(target.workflows)) {
        target.workflows.forEach((workflow, workflowIndex) => {
            workflow.steps.forEach((step, stepIndex) => {
                const requestBody = step.requestBody;
                if (!requestBody) {
                    return;
                }
                if (requestBody.contentType != null && !MIME_TYPE_REGEX.test(requestBody.contentType)) {
                    results.push({
                        message: `Invalid MIME type in contentType: ${requestBody.contentType}`,
                        path: ['workflows', workflowIndex, 'steps', stepIndex, 'requestBody', 'contentType'],
                    });
                }
                if (Boolean(requestBody.payload) &&
                    typeof requestBody.payload === 'string' &&
                    requestBody.payload.startsWith('$')) {
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(requestBody.payload, target, workflowIndex)) {
                        results.push({
                            message: `Invalid runtime expression in payload: ${requestBody.payload}`,
                            path: ['workflows', workflowIndex, 'steps', stepIndex, 'requestBody', 'payload'],
                        });
                    }
                }
                if (Array.isArray(requestBody.replacements)) {
                    requestBody.replacements.forEach((replacement, replacementIndex) => {
                        if (!replacement.target) {
                            results.push({
                                message: `"target" is required in Payload Replacement.`,
                                path: [
                                    'workflows',
                                    workflowIndex,
                                    'steps',
                                    stepIndex,
                                    'requestBody',
                                    'replacements',
                                    replacementIndex,
                                    'target',
                                ],
                            });
                        }
                        if (Boolean(replacement.value) &&
                            typeof replacement.value === 'string' &&
                            replacement.value.startsWith('$')) {
                            if (!(0, arazzoRuntimeExpressionValidation_1.default)(replacement.value, target, workflowIndex)) {
                                results.push({
                                    message: `Invalid runtime expression in replacement value: ${replacement.value}`,
                                    path: [
                                        'workflows',
                                        workflowIndex,
                                        'steps',
                                        stepIndex,
                                        'requestBody',
                                        'replacements',
                                        replacementIndex,
                                        'value',
                                    ],
                                });
                            }
                        }
                    });
                }
            });
        });
    }
    return results;
}
exports.arazzoStepRequestBodyValidation = arazzoStepRequestBodyValidation;
exports.default = arazzoStepRequestBodyValidation;
//# sourceMappingURL=arazzoStepRequestBodyValidation.js.map
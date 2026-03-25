"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_functions_1 = require("@stoplight/spectral-functions");
const mergeTraits_1 = require("./utils/mergeTraits");
function getMessageExamples(message) {
    var _a;
    if (!Array.isArray(message.examples)) {
        return [];
    }
    return ((_a = message.examples.map((example, index) => {
        return {
            path: ['examples', index],
            example,
        };
    })) !== null && _a !== void 0 ? _a : []);
}
function validate(value, path, type, schema, ctx) {
    return (0, spectral_functions_1.schema)(value, {
        allErrors: true,
        schema: schema,
    }, {
        ...ctx,
        path: [...ctx.path, ...path, type],
    });
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            name: {
                type: 'string',
            },
            summary: {
                type: 'string',
            },
        },
    },
    options: null,
}, function asyncApi2MessageExamplesValidation(targetVal, _, ctx) {
    var _a, _b;
    targetVal = (0, mergeTraits_1.mergeTraits)(targetVal);
    if (!targetVal.examples)
        return;
    const examples = getMessageExamples(targetVal);
    const results = [];
    for (const example of examples) {
        if (example.example.payload !== undefined) {
            const payload = (_a = targetVal.payload) !== null && _a !== void 0 ? _a : {};
            const errors = validate(example.example.payload, example.path, 'payload', payload, ctx);
            if (Array.isArray(errors)) {
                results.push(...errors);
            }
        }
        if (example.example.headers !== undefined) {
            const headers = (_b = targetVal.headers) !== null && _b !== void 0 ? _b : {};
            const errors = validate(example.example.headers, example.path, 'headers', headers, ctx);
            if (Array.isArray(errors)) {
                results.push(...errors);
            }
        }
    }
    return results;
});
//# sourceMappingURL=asyncApi2MessageExamplesValidation.js.map
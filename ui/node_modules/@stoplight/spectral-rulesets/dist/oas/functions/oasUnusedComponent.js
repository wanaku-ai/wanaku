"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_functions_1 = require("@stoplight/spectral-functions");
const spectral_core_1 = require("@stoplight/spectral-core");
const isObject_1 = require("./utils/isObject");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            components: {
                type: 'object',
            },
        },
        required: ['components'],
    },
    options: null,
}, function oasUnusedComponent(targetVal, opts, context) {
    const results = [];
    const componentTypes = [
        'schemas',
        'responses',
        'parameters',
        'examples',
        'requestBodies',
        'headers',
        'links',
        'callbacks',
    ];
    for (const type of componentTypes) {
        const value = targetVal.components[type];
        if (!(0, isObject_1.isObject)(value))
            continue;
        const resultsForType = (0, spectral_functions_1.unreferencedReusableObject)(value, { reusableObjectsLocation: `#/components/${type}` }, context);
        if (resultsForType !== void 0 && Array.isArray(resultsForType)) {
            results.push(...resultsForType);
        }
    }
    return results;
});
//# sourceMappingURL=oasUnusedComponent.js.map
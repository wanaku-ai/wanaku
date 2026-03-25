"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const parseUrlVariables_1 = require("../../shared/functions/serverVariables/utils/parseUrlVariables");
const getMissingProps_1 = require("../../shared/utils/getMissingProps");
const getRedundantProps_1 = require("../../shared/utils/getRedundantProps");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            parameters: {
                type: 'object',
            },
        },
        required: ['parameters'],
    },
    options: null,
}, function asyncApiChannelParameters(targetVal, _, ctx) {
    let path = ctx.path[ctx.path.length - 1];
    const results = [];
    if (targetVal.address !== null && targetVal.address !== undefined) {
        path = targetVal.address;
    }
    const parameters = (0, parseUrlVariables_1.parseUrlVariables)(path);
    if (parameters.length === 0)
        return;
    const missingParameters = (0, getMissingProps_1.getMissingProps)(parameters, Object.keys(targetVal.parameters));
    if (missingParameters.length) {
        results.push({
            message: `Not all channel's parameters are described with "parameters" object. Missed: ${missingParameters.join(', ')}.`,
            path: [...ctx.path, 'parameters'],
        });
    }
    const redundantParameters = (0, getRedundantProps_1.getRedundantProps)(parameters, Object.keys(targetVal.parameters));
    if (redundantParameters.length) {
        redundantParameters.forEach(param => {
            results.push({
                message: `Channel's "parameters" object has redundant defined "${param}" parameter.`,
                path: [...ctx.path, 'parameters', param],
            });
        });
    }
    return results;
});
//# sourceMappingURL=asyncApiChannelParameters.js.map
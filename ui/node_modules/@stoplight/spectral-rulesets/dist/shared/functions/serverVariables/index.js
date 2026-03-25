"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const parseUrlVariables_1 = require("./utils/parseUrlVariables");
const getMissingProps_1 = require("../../utils/getMissingProps");
const getRedundantProps_1 = require("../../utils/getRedundantProps");
const applyUrlVariables_1 = require("./utils/applyUrlVariables");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        errorMessage: 'Invalid Server Object',
        type: 'object',
        properties: {
            url: {
                type: 'string',
            },
            variables: {
                type: 'object',
                additionalProperties: {
                    type: 'object',
                    properties: {
                        enum: {
                            type: 'array',
                            items: {
                                type: 'string',
                            },
                        },
                        default: {
                            type: 'string',
                        },
                        description: {
                            type: 'string',
                        },
                        examples: {
                            type: 'string',
                        },
                    },
                    patternProperties: {
                        '^x-': true,
                    },
                    additionalProperties: false,
                },
            },
        },
        required: ['url'],
    },
    errorOnInvalidInput: true,
    options: {
        type: ['object', 'null'],
        properties: {
            checkSubstitutions: {
                type: 'boolean',
                default: 'false',
            },
            requireDefault: {
                type: 'boolean',
                default: 'false',
            },
        },
        additionalProperties: false,
    },
}, function serverVariables({ url, variables }, opts, ctx) {
    const results = [];
    const foundVariables = (0, parseUrlVariables_1.parseUrlVariables)(url);
    const definedVariablesKeys = variables === void 0 ? [] : Object.keys(variables);
    accumulateRedundantVariables(results, ctx.path, foundVariables, definedVariablesKeys);
    if (foundVariables.length === 0)
        return results;
    accumulateMissingVariables(results, ctx.path, foundVariables, definedVariablesKeys);
    if (variables === void 0)
        return results;
    const variablePairs = [];
    for (const key of definedVariablesKeys) {
        if (!foundVariables.includes(key))
            continue;
        const variable = variables[key];
        if ('enum' in variable) {
            variablePairs.push([key, variable.enum]);
            checkVariableEnumValues(results, ctx.path, key, variable.enum, variable.default);
        }
        else if ('default' in variable) {
            variablePairs.push([key, [variable.default]]);
        }
        else {
            variablePairs.push([key, []]);
        }
        if (!('default' in variable) && (opts === null || opts === void 0 ? void 0 : opts.requireDefault) === true) {
            results.push({
                message: `Server Variable "${key}" has a missing default.`,
                path: [...ctx.path, 'variables', key],
            });
        }
    }
    if ((opts === null || opts === void 0 ? void 0 : opts.checkSubstitutions) === true) {
        checkSubstitutions(results, ctx.path, url, variablePairs);
    }
    return results;
});
function accumulateRedundantVariables(results, path, foundVariables, definedVariablesKeys) {
    if (definedVariablesKeys.length === 0)
        return;
    const redundantVariables = (0, getRedundantProps_1.getRedundantProps)(foundVariables, definedVariablesKeys);
    for (const variable of redundantVariables) {
        results.push({
            message: `Server's "variables" object has unused defined "${variable}" url variable.`,
            path: [...path, 'variables', variable],
        });
    }
}
function accumulateMissingVariables(results, path, foundVariables, definedVariablesKeys) {
    const missingVariables = definedVariablesKeys.length === 0 ? foundVariables : (0, getMissingProps_1.getMissingProps)(foundVariables, definedVariablesKeys);
    if (missingVariables.length > 0) {
        results.push({
            message: `Not all server's variables are described with "variables" object. Missed: ${missingVariables.join(', ')}.`,
            path: [...path, 'variables'],
        });
    }
}
function checkVariableEnumValues(results, path, name, enumValues, defaultValue) {
    if (defaultValue !== void 0 && !enumValues.includes(defaultValue)) {
        results.push({
            message: `Server Variable "${name}" has a default not listed in the enum.`,
            path: [...path, 'variables', name, 'default'],
        });
    }
}
function checkSubstitutions(results, path, url, variables) {
    if (variables.length === 0)
        return;
    const invalidUrls = [];
    for (const substitutedUrl of (0, applyUrlVariables_1.applyUrlVariables)(url, variables)) {
        try {
            new URL(substitutedUrl);
        }
        catch {
            invalidUrls.push(substitutedUrl);
            if (invalidUrls.length === 5) {
                break;
            }
        }
    }
    if (invalidUrls.length === 5) {
        results.push({
            message: `At least 5 substitutions of server variables resulted in invalid URLs: ${invalidUrls.join(', ')} and more`,
            path: [...path, 'variables'],
        });
    }
    else if (invalidUrls.length > 0) {
        results.push({
            message: `A few substitutions of server variables resulted in invalid URLs: ${invalidUrls.join(', ')}`,
            path: [...path, 'variables'],
        });
    }
}
//# sourceMappingURL=index.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const json_1 = require("@stoplight/json");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        additionalProperties: {
            type: 'array',
            items: {
                type: 'string',
            },
        },
    },
    options: {
        type: 'object',
        properties: {
            oasVersion: {
                enum: [2, 3],
            },
        },
        additionalProperties: false,
    },
}, function oasSecurityDefined(input, { oasVersion }, { document, path }) {
    const schemeNames = Object.keys(input);
    if (schemeNames.length === 0)
        return;
    if (!(0, json_1.isPlainObject)(document.data))
        return;
    const allDefs = oasVersion === 2
        ? document.data.securityDefinitions
        : (0, json_1.isPlainObject)(document.data.components)
            ? document.data.components.securitySchemes
            : null;
    let results;
    for (const schemeName of schemeNames) {
        if (!(0, json_1.isPlainObject)(allDefs) || !(schemeName in allDefs)) {
            const object = path.length == 2 ? 'API' : 'Operation';
            const location = oasVersion === 2 ? 'securityDefinitions' : 'components.securitySchemes';
            results !== null && results !== void 0 ? results : (results = []);
            results.push({
                message: `${object} "security" values must match a scheme defined in the "${location}" object.`,
                path: [...path, schemeName],
            });
            continue;
        }
        const scope = input[schemeName];
        for (let i = 0; i < scope.length; i++) {
            const scopeName = scope[i];
            if (!isScopeDefined(oasVersion, scopeName, allDefs[schemeName])) {
                results !== null && results !== void 0 ? results : (results = []);
                results.push({
                    message: `"${scopeName}" must be listed among scopes.`,
                    path: [...path, schemeName, i],
                });
            }
        }
    }
    return results;
});
function isScopeDefined(oasVersion, scopeName, securityScheme) {
    if (!(0, json_1.isPlainObject)(securityScheme))
        return false;
    if (oasVersion === 2) {
        return (0, json_1.isPlainObject)(securityScheme.scopes) && scopeName in securityScheme.scopes;
    }
    if ((0, json_1.isPlainObject)(securityScheme.flows)) {
        for (const flow of Object.values(securityScheme.flows)) {
            if ((0, json_1.isPlainObject)(flow) && (0, json_1.isPlainObject)(flow.scopes) && scopeName in flow.scopes) {
                return true;
            }
        }
    }
    return false;
}
//# sourceMappingURL=oasSecurityDefined.js.map
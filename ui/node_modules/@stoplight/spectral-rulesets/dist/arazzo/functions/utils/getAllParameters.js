"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const json_1 = require("@stoplight/json");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("../arazzoRuntimeExpressionValidation"));
const resolveReusableParameter = (reusableObject, arazzoSpec) => {
    var _a, _b;
    const refPath = reusableObject.reference.replace('$components.parameters.', '');
    return (_b = (_a = arazzoSpec.components) === null || _a === void 0 ? void 0 : _a.parameters) === null || _b === void 0 ? void 0 : _b[refPath];
};
function isParameter(param) {
    if (typeof param === 'object' && param !== null) {
        const obj = param;
        return typeof obj.name === 'string' && (typeof obj.in === 'string' || obj.in === undefined);
    }
    return false;
}
function getAllParameters(step, workflow, arazzoSpec) {
    const resolvedParameters = [];
    const resolvedStepParameters = [];
    const processReusableParameter = (param) => {
        const paramName = param.reference;
        if (!(0, arazzoRuntimeExpressionValidation_1.default)(param.reference, arazzoSpec)) {
            return { name: `masked-invalid-reusable-parameter-reference-${paramName}` };
        }
        const resolvedParam = resolveReusableParameter(param, arazzoSpec);
        if (!resolvedParam) {
            return { name: `masked-unresolved-parameter-reference-${paramName}` };
        }
        return resolvedParam;
    };
    const resolveParameters = (params, targetArray) => {
        params.forEach(param => {
            let paramToPush;
            if ((0, json_1.isPlainObject)(param) && 'reference' in param) {
                paramToPush = processReusableParameter(param);
            }
            else {
                paramToPush = param;
            }
            if (isParameter(paramToPush)) {
                const isDuplicate = targetArray.some(existingParam => {
                    var _a, _b;
                    return isParameter(existingParam) &&
                        isParameter(paramToPush) &&
                        existingParam.name === paramToPush.name &&
                        ((_a = existingParam.in) !== null && _a !== void 0 ? _a : '') === ((_b = paramToPush.in) !== null && _b !== void 0 ? _b : '');
                });
                if (isDuplicate) {
                    paramToPush = {
                        ...paramToPush,
                        name: `masked-duplicate-${String(paramToPush.name)}`,
                    };
                }
                targetArray.push(paramToPush);
            }
        });
    };
    if (workflow.parameters != null) {
        resolveParameters(workflow.parameters, resolvedParameters);
    }
    if (step.parameters != null) {
        resolveParameters(step.parameters, resolvedStepParameters);
    }
    resolvedStepParameters.forEach(param => {
        const existingParamIndex = resolvedParameters.findIndex(p => { var _a, _b; return isParameter(p) && p.name === param.name && ((_a = p.in) !== null && _a !== void 0 ? _a : '') === ((_b = param.in) !== null && _b !== void 0 ? _b : ''); });
        if (existingParamIndex !== -1) {
            resolvedParameters[existingParamIndex] = param;
        }
        else {
            resolvedParameters.push(param);
        }
    });
    return resolvedParameters;
}
exports.default = getAllParameters;
//# sourceMappingURL=getAllParameters.js.map
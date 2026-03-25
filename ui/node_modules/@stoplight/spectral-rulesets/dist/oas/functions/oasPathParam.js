"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const isObject_1 = require("./utils/isObject");
const spectral_core_1 = require("@stoplight/spectral-core");
const pathRegex = /(\{;?\??[a-zA-Z0-9_-]+\*?\})/g;
const isNamedPathParam = (p) => {
    return p.in !== void 0 && p.in === 'path' && p.name !== void 0;
};
const isUnknownNamedPathParam = (p, path, results, seen) => {
    if (!isNamedPathParam(p)) {
        return false;
    }
    if (p.required !== true) {
        results.push(generateResult(requiredMessage(p.name), path));
    }
    if (p.name in seen) {
        results.push(generateResult(uniqueDefinitionMessage(p.name), path));
        return false;
    }
    return true;
};
const ensureAllDefinedPathParamsAreUsedInPath = (path, params, expected, results) => {
    for (const p of Object.keys(params)) {
        if (!params[p]) {
            continue;
        }
        if (!expected.includes(p)) {
            const resPath = params[p];
            results.push(generateResult(`Parameter "${p}" must be used in path "${path}".`, resPath));
        }
    }
};
const ensureAllExpectedParamsInPathAreDefined = (path, params, expected, operationPath, results) => {
    for (const p of expected) {
        if (!(p in params)) {
            results.push(generateResult(`Operation must define parameter "{${p}}" as expected by path "${path}".`, operationPath));
        }
    }
};
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
    },
    options: null,
}, function oasPathParam(paths) {
    const results = [];
    const uniquePaths = {};
    const validOperationKeys = ['get', 'head', 'post', 'put', 'patch', 'delete', 'options', 'trace'];
    for (const path of Object.keys(paths)) {
        const pathValue = paths[path];
        if (!(0, isObject_1.isObject)(pathValue))
            continue;
        const normalized = path.replace(pathRegex, '%');
        if (normalized in uniquePaths) {
            results.push(generateResult(`Paths "${String(uniquePaths[normalized])}" and "${path}" must not be equivalent.`, [
                'paths',
                path,
            ]));
        }
        else {
            uniquePaths[normalized] = path;
        }
        const pathElements = [];
        let match;
        while ((match = pathRegex.exec(path))) {
            const p = match[0].replace(/[{}?*;]/g, '');
            if (pathElements.includes(p)) {
                results.push(generateResult(`Path "${path}" must not use parameter "{${p}}" multiple times.`, ['paths', path]));
            }
            else {
                pathElements.push(p);
            }
        }
        const topParams = {};
        if (Array.isArray(pathValue.parameters)) {
            for (const [i, value] of pathValue.parameters.entries()) {
                if (!(0, isObject_1.isObject)(value))
                    continue;
                const fullParameterPath = ['paths', path, 'parameters', i];
                if (isUnknownNamedPathParam(value, fullParameterPath, results, topParams)) {
                    topParams[value.name] = fullParameterPath;
                }
            }
        }
        if ((0, isObject_1.isObject)(paths[path])) {
            for (const op of Object.keys(pathValue)) {
                const operationValue = pathValue[op];
                if (!(0, isObject_1.isObject)(operationValue))
                    continue;
                if (op === 'parameters' || !validOperationKeys.includes(op)) {
                    continue;
                }
                const operationParams = {};
                const { parameters } = operationValue;
                const operationPath = ['paths', path, op];
                if (Array.isArray(parameters)) {
                    for (const [i, p] of parameters.entries()) {
                        if (!(0, isObject_1.isObject)(p))
                            continue;
                        const fullParameterPath = [...operationPath, 'parameters', i];
                        if (isUnknownNamedPathParam(p, fullParameterPath, results, operationParams)) {
                            operationParams[p.name] = fullParameterPath;
                        }
                    }
                }
                const definedParams = { ...topParams, ...operationParams };
                ensureAllDefinedPathParamsAreUsedInPath(path, definedParams, pathElements, results);
                ensureAllExpectedParamsInPathAreDefined(path, definedParams, pathElements, operationPath, results);
            }
        }
    }
    return results;
});
function generateResult(message, path) {
    return {
        message,
        path,
    };
}
const requiredMessage = (name) => `Path parameter "${name}" must have "required" property that is set to "true".`;
const uniqueDefinitionMessage = (name) => `Path parameter "${name}" must not be defined multiple times.`;
//# sourceMappingURL=oasPathParam.js.map
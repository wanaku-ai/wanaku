"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const getAllOperations_1 = require("./utils/getAllOperations");
const isObject_1 = require("./utils/isObject");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
    },
    options: null,
}, function oasOpIdUnique(paths) {
    const results = [];
    const seenIds = [];
    for (const { path, operation } of (0, getAllOperations_1.getAllOperations)(paths)) {
        const pathValue = paths[path];
        if (!(0, isObject_1.isObject)(pathValue))
            continue;
        const operationValue = pathValue[operation];
        if (!(0, isObject_1.isObject)(operationValue) || !('operationId' in operationValue)) {
            continue;
        }
        const { operationId } = operationValue;
        if (seenIds.includes(operationId)) {
            results.push({
                message: 'operationId must be unique.',
                path: ['paths', path, operation, 'operationId'],
            });
        }
        else {
            seenIds.push(operationId);
        }
    }
    return results;
});
//# sourceMappingURL=oasOpIdUnique.js.map
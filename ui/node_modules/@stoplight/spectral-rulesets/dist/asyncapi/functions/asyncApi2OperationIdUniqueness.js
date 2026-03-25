"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const json_1 = require("@stoplight/json");
const getAllOperations_1 = require("./utils/getAllOperations");
function retrieveOperationId(operation) {
    if (Array.isArray(operation.traits)) {
        for (let i = operation.traits.length - 1; i >= 0; i--) {
            const trait = operation.traits[i];
            if ((0, json_1.isPlainObject)(trait) && typeof trait.operationId === 'string') {
                return {
                    operationId: trait.operationId,
                    path: ['traits', i, 'operationId'],
                };
            }
        }
    }
    if (typeof operation.operationId === 'string') {
        return {
            operationId: operation.operationId,
            path: ['operationId'],
        };
    }
    return undefined;
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            channels: {
                type: 'object',
                properties: {
                    subscribe: {
                        type: 'object',
                    },
                    publish: {
                        type: 'object',
                    },
                },
            },
        },
    },
    options: null,
}, function asyncApi2OperationIdUniqueness(targetVal, _) {
    const results = [];
    const operations = (0, getAllOperations_1.getAllOperations)(targetVal);
    const seenIds = [];
    for (const { path, operation } of operations) {
        const maybeOperationId = retrieveOperationId(operation);
        if (maybeOperationId === undefined) {
            continue;
        }
        if (seenIds.includes(maybeOperationId.operationId)) {
            results.push({
                message: '"operationId" must be unique across all the operations.',
                path: [...path, ...maybeOperationId.path],
            });
        }
        else {
            seenIds.push(maybeOperationId.operationId);
        }
    }
    return results;
});
//# sourceMappingURL=asyncApi2OperationIdUniqueness.js.map
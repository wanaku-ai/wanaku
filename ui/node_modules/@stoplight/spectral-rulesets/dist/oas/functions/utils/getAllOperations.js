"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getAllOperations = void 0;
const json_1 = require("@stoplight/json");
const validOperationKeys = ['get', 'head', 'post', 'put', 'patch', 'delete', 'options', 'trace'];
function* getAllOperations(paths) {
    if (!(0, json_1.isPlainObject)(paths)) {
        return;
    }
    const item = {
        path: '',
        operation: '',
        value: null,
    };
    for (const path of Object.keys(paths)) {
        const operations = paths[path];
        if (!(0, json_1.isPlainObject)(operations)) {
            continue;
        }
        item.path = path;
        for (const operation of Object.keys(operations)) {
            if (!(0, json_1.isPlainObject)(operations[operation]) || !validOperationKeys.includes(operation)) {
                continue;
            }
            item.operation = operation;
            item.value = operations[operation];
            yield item;
        }
    }
}
exports.getAllOperations = getAllOperations;
//# sourceMappingURL=getAllOperations.js.map
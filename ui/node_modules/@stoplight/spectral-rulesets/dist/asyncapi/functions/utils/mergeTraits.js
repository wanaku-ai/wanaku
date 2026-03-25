"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.mergeTraits = void 0;
const json_1 = require("@stoplight/json");
function mergeTraits(data) {
    if (Array.isArray(data.traits)) {
        data = { ...data };
        for (const trait of data.traits) {
            for (const key in trait) {
                data[key] = merge(data[key], trait[key]);
            }
        }
    }
    return data;
}
exports.mergeTraits = mergeTraits;
function merge(origin, patch) {
    if (!(0, json_1.isPlainObject)(patch)) {
        return patch;
    }
    const result = !(0, json_1.isPlainObject)(origin)
        ? {}
        : Object.assign({}, origin);
    Object.keys(patch).forEach(key => {
        const patchVal = patch[key];
        if (patchVal === null) {
            delete result[key];
        }
        else {
            result[key] = merge(result[key], patchVal);
        }
    });
    return result;
}
//# sourceMappingURL=mergeTraits.js.map
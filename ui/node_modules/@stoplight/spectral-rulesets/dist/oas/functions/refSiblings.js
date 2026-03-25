"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const isObject_1 = require("./utils/isObject");
function getParentValue(document, path) {
    if (path.length === 0) {
        return null;
    }
    let piece = document;
    for (let i = 0; i < path.length - 1; i += 1) {
        if (!(0, isObject_1.isObject)(piece)) {
            return null;
        }
        piece = piece[path[i]];
    }
    return piece;
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: null,
    options: null,
}, function refSiblings(targetVal, opts, { document, path }) {
    const value = getParentValue(document.data, path);
    if (!(0, isObject_1.isObject)(value))
        return;
    const keys = Object.keys(value);
    if (keys.length === 1) {
        return;
    }
    const results = [];
    const actualObjPath = path.slice(0, -1);
    for (const key of keys) {
        if (key === '$ref') {
            continue;
        }
        results.push({
            message: '$ref must not be placed next to any other properties',
            path: [...actualObjPath, key],
        });
    }
    return results;
});
//# sourceMappingURL=refSiblings.js.map
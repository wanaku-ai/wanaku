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
}, function oasTagDefined(targetVal) {
    const results = [];
    const globalTags = [];
    if (Array.isArray(targetVal.tags)) {
        for (const tag of targetVal.tags) {
            if ((0, isObject_1.isObject)(tag) && typeof tag.name === 'string') {
                globalTags.push(tag.name);
            }
        }
    }
    const { paths } = targetVal;
    for (const { path, operation, value } of (0, getAllOperations_1.getAllOperations)(paths)) {
        if (!(0, isObject_1.isObject)(value))
            continue;
        const { tags } = value;
        if (!Array.isArray(tags)) {
            continue;
        }
        for (const [i, tag] of tags.entries()) {
            if (!globalTags.includes(tag)) {
                results.push({
                    message: 'Operation tags must be defined in global tags.',
                    path: ['paths', path, operation, 'tags', i],
                });
            }
        }
    }
    return results;
});
//# sourceMappingURL=oasTagDefined.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const isObject_1 = require("./utils/isObject");
const spectral_core_1 = require("@stoplight/spectral-core");
function computeFingerprint(param) {
    return `${String(param.in)}-${String(param.name)}`;
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'array',
    },
    options: null,
}, function oasOpParams(params, _opts, { path }) {
    if (!Array.isArray(params))
        return;
    if (params.length < 2)
        return;
    const results = [];
    const count = {
        body: [],
        formData: [],
    };
    const list = [];
    const duplicates = [];
    let index = -1;
    for (const param of params) {
        index++;
        if (!(0, isObject_1.isObject)(param))
            continue;
        if ('$ref' in param)
            continue;
        const fingerprint = computeFingerprint(param);
        if (list.includes(fingerprint)) {
            duplicates.push(index);
        }
        else {
            list.push(fingerprint);
        }
        if (typeof param.in === 'string' && param.in in count) {
            count[param.in].push(index);
        }
    }
    if (duplicates.length > 0) {
        for (const i of duplicates) {
            results.push({
                message: 'A parameter in this operation already exposes the same combination of "name" and "in" values.',
                path: [...path, i],
            });
        }
    }
    if (count.body.length > 0 && count.formData.length > 0) {
        results.push({
            message: 'Operation must not have both "in:body" and "in:formData" parameters.',
        });
    }
    if (count.body.length > 1) {
        for (let i = 1; i < count.body.length; i++) {
            results.push({
                message: 'Operation must not have more than a single instance of the "in:body" parameter.',
                path: [...path, count.body[i]],
            });
        }
    }
    return results;
});
//# sourceMappingURL=oasOpParams.js.map
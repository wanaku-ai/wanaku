"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const isObject_1 = require("./utils/isObject");
const validConsumeValue = /(application\/x-www-form-urlencoded|multipart\/form-data)/;
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            consumes: {
                type: 'array',
            },
            parameters: {
                type: 'array',
            },
        },
        required: ['consumes', 'parameters'],
    },
    options: null,
}, function oasOpFormDataConsumeCheck({ parameters, consumes }) {
    if (parameters.some(p => (0, isObject_1.isObject)(p) && p.in === 'formData') && !validConsumeValue.test(consumes === null || consumes === void 0 ? void 0 : consumes.join(','))) {
        return [
            {
                message: 'Consumes must include urlencoded, multipart, or form-data media type when using formData parameter.',
            },
        ];
    }
    return;
});
//# sourceMappingURL=oasOpFormDataConsumeCheck.js.map
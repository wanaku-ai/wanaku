"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const isObject_1 = require("./utils/isObject");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            discriminator: {
                type: 'string',
            },
        },
        required: ['discriminator'],
    },
    options: null,
}, function oasDiscriminator(schema, _opts, { path }) {
    const discriminatorName = schema.discriminator;
    const results = [];
    if (!(0, isObject_1.isObject)(schema.properties) || !Object.keys(schema.properties).some(k => k === discriminatorName)) {
        results.push({
            message: `The discriminator property must be defined in this schema.`,
            path: [...path, 'properties'],
        });
    }
    if (!Array.isArray(schema.required) || !schema.required.some(n => n === discriminatorName)) {
        results.push({
            message: `The discriminator property must be in the required property list.`,
            path: [...path, 'required'],
        });
    }
    return results;
});
//# sourceMappingURL=oasDiscriminator.js.map
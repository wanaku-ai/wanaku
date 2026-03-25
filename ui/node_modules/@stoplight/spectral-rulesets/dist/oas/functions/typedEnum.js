"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_formats_1 = require("@stoplight/spectral-formats");
const spectral_runtime_1 = require("@stoplight/spectral-runtime");
const spectral_core_1 = require("@stoplight/spectral-core");
function getDataType(input, checkForInteger) {
    const type = typeof input;
    switch (type) {
        case 'string':
        case 'boolean':
            return type;
        case 'number':
            if (checkForInteger && Number.isInteger(input)) {
                return 'integer';
            }
            return 'number';
        case 'object':
            if (input === null) {
                return 'null';
            }
            return Array.isArray(input) ? 'array' : 'object';
        default:
            throw TypeError('Unknown input type');
    }
}
function getTypes(input, formats) {
    const { type } = input;
    if ((input.nullable === true && (formats === null || formats === void 0 ? void 0 : formats.has(spectral_formats_1.oas3_0)) === true) ||
        (input['x-nullable'] === true && (formats === null || formats === void 0 ? void 0 : formats.has(spectral_formats_1.oas2)) === true)) {
        return Array.isArray(type) ? [...type, 'null'] : [type, 'null'];
    }
    return type;
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            enum: {
                type: 'array',
            },
            type: {
                oneOf: [
                    {
                        type: 'array',
                        items: {
                            type: 'string',
                        },
                    },
                    {
                        type: 'string',
                    },
                ],
            },
        },
        required: ['enum', 'type'],
    },
    options: null,
}, function typedEnum(input, opts, context) {
    const { enum: enumValues } = input;
    const type = getTypes(input, context.document.formats);
    const checkForInteger = type === 'integer' || (Array.isArray(type) && type.includes('integer'));
    let results;
    enumValues.forEach((value, i) => {
        const valueType = getDataType(value, checkForInteger);
        if (valueType === type || (Array.isArray(type) && type.includes(valueType))) {
            return;
        }
        results !== null && results !== void 0 ? results : (results = []);
        results.push({
            message: `Enum value ${(0, spectral_runtime_1.printValue)(enumValues[i])} must be "${String(type)}".`,
            path: [...context.path, 'enum', i],
        });
    });
    return results;
});
//# sourceMappingURL=typedEnum.js.map
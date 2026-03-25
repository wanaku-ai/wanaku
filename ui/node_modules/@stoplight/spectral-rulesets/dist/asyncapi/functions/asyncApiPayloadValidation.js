"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const ajv_1 = (0, tslib_1.__importDefault)(require("ajv"));
const ajv_formats_1 = (0, tslib_1.__importDefault)(require("ajv-formats"));
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const better_ajv_errors_1 = (0, tslib_1.__importDefault)(require("@stoplight/better-ajv-errors"));
const specs_1 = require("./utils/specs");
const asyncApi2SchemaObject = { $ref: 'asyncapi2#/definitions/schema' };
const ajv = new ajv_1.default({
    allErrors: true,
    strict: false,
    logger: false,
});
(0, ajv_formats_1.default)(ajv);
function preparePayloadSchema(version) {
    const copied = (0, specs_1.getCopyOfSchema)(version);
    delete copied.definitions['http://json-schema.org/draft-07/schema'];
    delete copied.definitions['http://json-schema.org/draft-04/schema'];
    const payloadSchema = `http://asyncapi.com/definitions/${version}/schema.json`;
    return {
        $ref: payloadSchema,
        definitions: copied.definitions,
    };
}
function getValidator(version) {
    let validator = ajv.getSchema(version);
    if (!validator) {
        const schema = preparePayloadSchema(version);
        ajv.addSchema(schema, version);
        validator = ajv.getSchema(version);
    }
    return validator;
}
function getSchemaValidator(formats) {
    switch (true) {
        case formats.has(spectral_formats_1.aas3_0):
            return getValidator('3.0.0');
        case formats.has(spectral_formats_1.aas2_6):
            return getValidator('2.6.0');
        case formats.has(spectral_formats_1.aas2_5):
            return getValidator('2.5.0');
        case formats.has(spectral_formats_1.aas2_4):
            return getValidator('2.4.0');
        case formats.has(spectral_formats_1.aas2_3):
            return getValidator('2.3.0');
        case formats.has(spectral_formats_1.aas2_2):
            return getValidator('2.2.0');
        case formats.has(spectral_formats_1.aas2_1):
            return getValidator('2.1.0');
        case formats.has(spectral_formats_1.aas2_0):
            return getValidator('2.0.0');
        default:
            return;
    }
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: null,
    options: null,
}, function asyncApiPayloadValidation(targetVal, _, context) {
    var _a;
    const formats = (_a = context.document) === null || _a === void 0 ? void 0 : _a.formats;
    if (formats === null || formats === void 0)
        return;
    const validator = getSchemaValidator(formats);
    if (validator === void 0)
        return;
    validator(targetVal);
    return (0, better_ajv_errors_1.default)(asyncApi2SchemaObject, validator.errors, {
        propertyPath: context.path,
        targetValue: targetVal,
    }).map(({ suggestion, error, path: errorPath }) => ({
        message: suggestion !== void 0 ? `${error}. ${suggestion}` : error,
        path: [...context.path, ...(errorPath !== '' ? errorPath.replace(/^\//, '').split('/') : [])],
    }));
});
//# sourceMappingURL=asyncApiPayloadValidation.js.map
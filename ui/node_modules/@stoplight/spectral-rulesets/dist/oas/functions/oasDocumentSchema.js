"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const json_1 = require("@stoplight/json");
const leven_1 = (0, tslib_1.__importDefault)(require("leven"));
const validators = (0, tslib_1.__importStar)(require("../schemas/validators"));
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: null,
    options: null,
}, function oasDocumentSchema(input, _opts, context) {
    const formats = context.document.formats;
    if (formats === null || formats === void 0)
        return;
    const schema = formats.has(spectral_formats_1.oas2) ? 'oas2_0' : formats.has(spectral_formats_1.oas3_1) ? 'oas3_1' : 'oas3_0';
    const validator = validators[schema];
    validator(input);
    const errors = validator['errors'];
    return errors === null || errors === void 0 ? void 0 : errors.reduce((errors, e) => processError(errors, input, schema, e), []);
});
function isRelevantError(error) {
    return error.keyword !== 'if';
}
function processError(errors, input, schema, error) {
    if (!isRelevantError(error)) {
        return errors;
    }
    const path = error.instancePath === '' ? [] : error.instancePath.slice(1).split('/');
    const property = path.length === 0 ? null : path[path.length - 1];
    let message;
    switch (error.keyword) {
        case 'additionalProperties': {
            const additionalProperty = error.params['additionalProperty'];
            path.push(additionalProperty);
            message = `Property "${additionalProperty}" is not expected to be here`;
            break;
        }
        case 'enum': {
            const allowedValues = error.params['allowedValues'];
            const printedValues = allowedValues.map(value => JSON.stringify(value)).join(', ');
            let suggestion;
            if (!(0, json_1.isPlainObject)(input)) {
                suggestion = '';
            }
            else {
                const value = (0, json_1.resolveInlineRef)(input, `#${error.instancePath}`);
                if (typeof value !== 'string') {
                    suggestion = '';
                }
                else {
                    const bestMatch = findBestMatch(value, allowedValues);
                    if (bestMatch !== null) {
                        suggestion = `. Did you mean "${bestMatch}"?`;
                    }
                    else {
                        suggestion = '';
                    }
                }
            }
            message = `${cleanAjvMessage(property, error.message)}: ${printedValues}${suggestion}`;
            break;
        }
        case 'errorMessage':
            message = String(error.message);
            break;
        default:
            message = cleanAjvMessage(property, error.message);
    }
    errors.push({
        message,
        path,
    });
    return errors;
}
function findBestMatch(value, allowedValues) {
    const matches = allowedValues
        .filter((value) => typeof value === 'string')
        .map(allowedValue => ({
        value: allowedValue,
        weight: (0, leven_1.default)(value, allowedValue),
    }))
        .sort((x, y) => (x.weight > y.weight ? 1 : x.weight < y.weight ? -1 : 0));
    if (matches.length === 0) {
        return null;
    }
    const bestMatch = matches[0];
    return allowedValues.length === 1 || bestMatch.weight < bestMatch.value.length ? bestMatch.value : null;
}
const QUOTES = /['"]/g;
const NOT = /NOT/g;
function cleanAjvMessage(prop, message) {
    if (typeof message !== 'string')
        return '';
    const cleanedMessage = message.replace(QUOTES, '"').replace(NOT, 'not');
    return prop === null ? cleanedMessage : `"${prop}" property ${cleanedMessage}`;
}
//# sourceMappingURL=oasDocumentSchema.js.map
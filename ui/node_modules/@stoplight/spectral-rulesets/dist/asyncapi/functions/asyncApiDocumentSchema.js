"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.asyncApiDocumentSchema = exports.getSchema = exports.prepareResults = void 0;
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_functions_1 = require("@stoplight/spectral-functions");
const specs_1 = require("./utils/specs");
const spectral_formats_1 = require("@stoplight/spectral-formats");
function shouldIgnoreError(error) {
    return (error.keyword === 'oneOf' ||
        (error.keyword === 'required' && error.params.missingProperty === '$ref'));
}
function prepareResults(errors) {
    for (let i = 0; i < errors.length; i++) {
        const error = errors[i];
        if (error.keyword === 'additionalProperties') {
            error.instancePath = `${error.instancePath}/${String(error.params['additionalProperty'])}`;
        }
        else if (error.keyword === 'required' && error.params.missingProperty === '$ref') {
            errors.splice(i, 1);
            i--;
        }
    }
    for (let i = 0; i < errors.length; i++) {
        const error = errors[i];
        if (i + 1 < errors.length && errors[i + 1].instancePath === error.instancePath) {
            errors.splice(i + 1, 1);
            i--;
        }
        else if (i > 0 && shouldIgnoreError(error) && errors[i - 1].instancePath.startsWith(error.instancePath)) {
            errors.splice(i, 1);
            i--;
        }
    }
}
exports.prepareResults = prepareResults;
function prepareV3ResolvedSchema(copied) {
    const channelObject = copied.definitions['http://asyncapi.com/definitions/3.0.0/channel.json'];
    channelObject.properties.servers.items.$ref = 'http://asyncapi.com/definitions/3.0.0/server.json';
    const operationSchema = copied.definitions['http://asyncapi.com/definitions/3.0.0/operation.json'];
    operationSchema.properties.channel.$ref = 'http://asyncapi.com/definitions/3.0.0/channel.json';
    operationSchema.properties.messages.items.$ref = 'http://asyncapi.com/definitions/3.0.0/messageObject.json';
    const operationReplySchema = copied.definitions['http://asyncapi.com/definitions/3.0.0/operationReply.json'];
    operationReplySchema.properties.channel.$ref = 'http://asyncapi.com/definitions/3.0.0/channel.json';
    operationReplySchema.properties.messages.items.$ref = 'http://asyncapi.com/definitions/3.0.0/messageObject.json';
    return copied;
}
const serializedSchemas = new Map();
function getSerializedSchema(version, resolved) {
    const serializedSchemaKey = resolved ? `${version}-resolved` : `${version}-unresolved`;
    const schema = serializedSchemas.get(serializedSchemaKey);
    if (schema) {
        return schema;
    }
    let copied = (0, specs_1.getCopyOfSchema)(version);
    delete copied.definitions['http://json-schema.org/draft-07/schema'];
    delete copied.definitions['http://json-schema.org/draft-04/schema'];
    copied['$id'] = copied['$id'].replace('asyncapi.json', `asyncapi-${resolved ? 'resolved' : 'unresolved'}.json`);
    if (resolved && version === '3.0.0') {
        copied = prepareV3ResolvedSchema(copied);
    }
    serializedSchemas.set(serializedSchemaKey, copied);
    return copied;
}
const refErrorMessage = 'Property "$ref" is not expected to be here';
function filterRefErrors(errors, resolved) {
    if (resolved) {
        return errors.filter(err => err.message !== refErrorMessage);
    }
    return errors
        .filter(err => err.message === refErrorMessage)
        .map(err => {
        err.message = 'Referencing in this place is not allowed';
        return err;
    });
}
function getSchema(formats, resolved) {
    switch (true) {
        case formats.has(spectral_formats_1.aas3_0):
            return getSerializedSchema('3.0.0', resolved);
        case formats.has(spectral_formats_1.aas2_6):
            return getSerializedSchema('2.6.0', resolved);
        case formats.has(spectral_formats_1.aas2_5):
            return getSerializedSchema('2.5.0', resolved);
        case formats.has(spectral_formats_1.aas2_4):
            return getSerializedSchema('2.4.0', resolved);
        case formats.has(spectral_formats_1.aas2_3):
            return getSerializedSchema('2.3.0', resolved);
        case formats.has(spectral_formats_1.aas2_2):
            return getSerializedSchema('2.2.0', resolved);
        case formats.has(spectral_formats_1.aas2_1):
            return getSerializedSchema('2.1.0', resolved);
        case formats.has(spectral_formats_1.aas2_0):
            return getSerializedSchema('2.0.0', resolved);
        default:
            return;
    }
}
exports.getSchema = getSchema;
exports.asyncApiDocumentSchema = (0, spectral_core_1.createRulesetFunction)({
    input: null,
    options: {
        type: 'object',
        properties: {
            resolved: {
                type: 'boolean',
            },
        },
        required: ['resolved'],
    },
}, (targetVal, options, context) => {
    var _a;
    const formats = (_a = context.document) === null || _a === void 0 ? void 0 : _a.formats;
    if (!formats) {
        return;
    }
    const resolved = options.resolved;
    const schema = getSchema(formats, resolved);
    if (!schema) {
        return;
    }
    const errors = (0, spectral_functions_1.schema)(targetVal, { allErrors: true, schema, prepareResults: resolved ? prepareResults : undefined }, context);
    if (!Array.isArray(errors)) {
        return;
    }
    return filterRefErrors(errors, resolved);
});
//# sourceMappingURL=asyncApiDocumentSchema.js.map
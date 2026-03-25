"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const json_schema_traverse_1 = (0, tslib_1.__importDefault)(require("json-schema-traverse"));
const spectral_functions_1 = require("@stoplight/spectral-functions");
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const json_1 = require("@stoplight/json");
function rewriteNullable(schema, errors) {
    for (const error of errors) {
        if (error.keyword !== 'type')
            continue;
        const value = getSchemaProperty(schema, error.schemaPath);
        if ((0, json_1.isPlainObject)(value) && value.nullable === true) {
            error.message += ',null';
        }
    }
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: null,
    options: {
        type: 'object',
        properties: {
            schema: {
                type: 'object',
            },
        },
        additionalProperties: false,
    },
}, function oasSchema(targetVal, opts, context) {
    var _a;
    const formats = context.document.formats;
    let { schema } = opts;
    let dialect = 'draft4';
    let prepareResults;
    if (!formats) {
        dialect = 'auto';
    }
    else if (formats.has(spectral_formats_1.oas3_1)) {
        if ((0, json_1.isPlainObject)(context.document.data) && typeof context.document.data.jsonSchemaDialect === 'string') {
            dialect =
                (_a = (0, spectral_formats_1.extractDraftVersion)(context.document.data.jsonSchemaDialect)) !== null && _a !== void 0 ? _a : 'draft2020-12';
        }
        else {
            dialect = 'draft2020-12';
        }
    }
    else if (formats.has(spectral_formats_1.oas3_0)) {
        prepareResults = rewriteNullable.bind(null, schema);
    }
    else if (formats.has(spectral_formats_1.oas2)) {
        const clonedSchema = JSON.parse(JSON.stringify(schema));
        (0, json_schema_traverse_1.default)(clonedSchema, visitOAS2);
        schema = clonedSchema;
        prepareResults = rewriteNullable.bind(null, clonedSchema);
    }
    return (0, spectral_functions_1.schema)(targetVal, {
        ...opts,
        schema,
        prepareResults,
        dialect,
    }, context);
});
const visitOAS2 = schema => {
    if (schema['x-nullable'] === true) {
        schema.nullable = true;
        delete schema['x-nullable'];
    }
};
function getSchemaProperty(schema, schemaPath) {
    const path = (0, json_1.pointerToPath)(schemaPath);
    let value = schema;
    for (const fragment of path.slice(0, -1)) {
        if (!(0, json_1.isPlainObject)(value)) {
            return;
        }
        value = value[fragment];
    }
    return value;
}
//# sourceMappingURL=oasSchema.js.map
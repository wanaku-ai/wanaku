"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const isObject_1 = require("./utils/isObject");
const oasSchema_1 = (0, tslib_1.__importDefault)(require("./oasSchema"));
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_formats_1 = require("@stoplight/spectral-formats");
const json_schema_traverse_1 = (0, tslib_1.__importDefault)(require("json-schema-traverse"));
const MEDIA_VALIDATION_ITEMS = {
    2: [
        {
            field: 'examples',
            multiple: true,
            keyed: false,
        },
    ],
    3: [
        {
            field: 'example',
            multiple: false,
            keyed: false,
        },
        {
            field: 'examples',
            multiple: true,
            keyed: true,
        },
    ],
};
const REQUEST_MEDIA_PATHS = {
    2: [],
    3: [
        ['components', 'requestBodies'],
        ['paths', '*', '*', 'requestBody'],
    ],
};
const RESPONSE_MEDIA_PATHS = {
    2: [['responses'], ['paths', '*', '*', 'responses']],
    3: [
        ['components', 'responses'],
        ['paths', '*', '*', 'responses'],
    ],
};
const SCHEMA_VALIDATION_ITEMS = {
    2: ['example', 'x-example', 'default'],
    3: ['example', 'default'],
};
function hasRequiredProperties(schema) {
    return schema.required === undefined || Array.isArray(schema.required);
}
function isSubpath(path, subPaths) {
    return subPaths.some(subPath => subPath.every((segment, idx) => segment === '*' || segment === path[idx]));
}
function isMediaRequest(path, oasVersion) {
    return isSubpath(path, REQUEST_MEDIA_PATHS[oasVersion]);
}
function isMediaResponse(path, oasVersion) {
    return isSubpath(path, RESPONSE_MEDIA_PATHS[oasVersion]);
}
function* getMediaValidationItems(items, targetVal, givenPath, oasVersion) {
    for (const { field, keyed, multiple } of items) {
        if (!(field in targetVal)) {
            continue;
        }
        const value = targetVal[field];
        if (multiple) {
            if (!(0, isObject_1.isObject)(value))
                continue;
            for (const exampleKey of Object.keys(value)) {
                const exampleValue = value[exampleKey];
                if (oasVersion === 3 && keyed && (!(0, isObject_1.isObject)(exampleValue) || 'externalValue' in exampleValue)) {
                    continue;
                }
                const targetPath = [...givenPath, field, exampleKey];
                if (keyed) {
                    targetPath.push('value');
                }
                yield {
                    value: keyed && (0, isObject_1.isObject)(exampleValue) ? exampleValue.value : exampleValue,
                    path: targetPath,
                };
            }
            return;
        }
        else {
            return yield {
                value,
                path: [...givenPath, field],
            };
        }
    }
}
function* getSchemaValidationItems(fields, targetVal, givenPath) {
    for (const field of fields) {
        if (!(field in targetVal)) {
            continue;
        }
        yield {
            value: targetVal[field],
            path: [...givenPath, field],
        };
    }
}
const KNOWN_TRAVERSE_KEYWORDS = [
    ...Object.keys(json_schema_traverse_1.default['keywords']),
    ...Object.keys(json_schema_traverse_1.default['arrayKeywords']),
    ...Object.keys(json_schema_traverse_1.default['propsKeywords']),
];
function cleanSchema(schema) {
    (0, json_schema_traverse_1.default)(schema, { allKeys: true }, ((fragment, jsonPtr, rootSchema, parentJsonPtr, parentKeyword) => {
        if (parentKeyword === void 0 || KNOWN_TRAVERSE_KEYWORDS.includes(parentKeyword))
            return;
        if ('id' in fragment) {
            delete fragment.id;
        }
        if ('$id' in fragment) {
            delete fragment.id;
        }
    }));
}
function relaxRequired(schema, readOnlyProperties, writeOnlyProperties) {
    if (readOnlyProperties || writeOnlyProperties)
        (0, json_schema_traverse_1.default)(schema, {}, ((fragment, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parent, propertyName) => {
            var _a, _b;
            if ((fragment.readOnly === true && readOnlyProperties) || (fragment.writeOnly === true && writeOnlyProperties)) {
                if (parentKeyword == 'properties' && parent && hasRequiredProperties(parent)) {
                    parent.required = (_a = parent.required) === null || _a === void 0 ? void 0 : _a.filter(p => p !== propertyName);
                    if (((_b = parent.required) === null || _b === void 0 ? void 0 : _b.length) === 0) {
                        delete parent.required;
                    }
                }
            }
        }));
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
    },
    options: {
        type: 'object',
        properties: {
            oasVersion: {
                enum: [2, 3],
            },
            schemaField: {
                type: 'string',
            },
            type: {
                enum: ['media', 'schema'],
            },
        },
        additionalProperties: false,
    },
}, function oasExample(targetVal, opts, context) {
    const formats = context.document.formats;
    const schemaOpts = {
        schema: opts.schemaField === '$' ? targetVal : targetVal[opts.schemaField],
    };
    let results = void 0;
    const validationItems = opts.type === 'schema'
        ? getSchemaValidationItems(SCHEMA_VALIDATION_ITEMS[opts.oasVersion], targetVal, context.path)
        : getMediaValidationItems(MEDIA_VALIDATION_ITEMS[opts.oasVersion], targetVal, context.path, opts.oasVersion);
    if ((formats === null || formats === void 0 ? void 0 : formats.has(spectral_formats_1.oas2)) && 'required' in schemaOpts.schema && typeof schemaOpts.schema.required === 'boolean') {
        schemaOpts.schema = { ...schemaOpts.schema };
        delete schemaOpts.schema.required;
    }
    schemaOpts.schema = JSON.parse(JSON.stringify(schemaOpts.schema));
    cleanSchema(schemaOpts.schema);
    relaxRequired(schemaOpts.schema, opts.type === 'media' && isMediaRequest(context.path, opts.oasVersion), opts.type === 'media' && isMediaResponse(context.path, opts.oasVersion));
    for (const validationItem of validationItems) {
        const result = (0, oasSchema_1.default)(validationItem.value, schemaOpts, {
            ...context,
            path: validationItem.path,
        });
        if (Array.isArray(result)) {
            if (results === void 0)
                results = [];
            results.push(...result);
        }
    }
    return results;
});
//# sourceMappingURL=oasExample.js.map
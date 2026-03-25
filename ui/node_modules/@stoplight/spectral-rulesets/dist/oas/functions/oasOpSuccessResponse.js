"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const spectral_formats_1 = require("@stoplight/spectral-formats");
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
    },
    options: null,
}, function oasOpSuccessResponse(input, opts, context) {
    var _a;
    const isOAS3X = ((_a = context.document.formats) === null || _a === void 0 ? void 0 : _a.has(spectral_formats_1.oas3)) === true;
    for (const response of Object.keys(input)) {
        if (isOAS3X && (response === '2XX' || response === '3XX')) {
            return;
        }
        if (Number(response) >= 200 && Number(response) < 400) {
            return;
        }
    }
    return [
        {
            message: 'Operation must define at least a single 2xx or 3xx response',
        },
    ];
});
//# sourceMappingURL=oasOpSuccessResponse.js.map
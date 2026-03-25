"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
function arazzoCriterionValidation(criterion, contextPath, arazzoSpec) {
    const results = [];
    if (!criterion.condition || typeof criterion.condition !== 'string' || criterion.condition.trim() === '') {
        results.push({
            message: `Missing or invalid "condition" in Criterion Object.`,
            path: [...contextPath, 'condition'],
        });
    }
    if (criterion.type !== undefined && criterion.type !== null && criterion.context == null) {
        results.push({
            message: `A "context" must be specified for a Criterion Object with type "${criterion.type}".`,
            path: [...contextPath, 'context'],
        });
    }
    if (typeof criterion.type === 'object') {
        const { type, version } = criterion.type;
        if (!type || !version) {
            results.push({
                message: `"type" and "version" must be specified in the Criterion Expression Type Object.`,
                path: [...contextPath, 'type'],
            });
        }
    }
    if (criterion.type === 'regex') {
        try {
            new RegExp(criterion.condition);
        }
        catch {
            results.push({
                message: `"condition" contains an invalid regex pattern.`,
                path: [...contextPath, 'condition'],
            });
        }
    }
    if (criterion.context != null && !(0, arazzoRuntimeExpressionValidation_1.default)(criterion.context, arazzoSpec)) {
        results.push({
            message: `"context" contains an invalid runtime expression.`,
            path: [...contextPath, 'context'],
        });
    }
    return results;
}
exports.default = arazzoCriterionValidation;
//# sourceMappingURL=arazzoCriterionValidation.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getAllWorkflows = void 0;
const json_1 = require("@stoplight/json");
function* getAllWorkflows(arazzo) {
    const workflows = arazzo === null || arazzo === void 0 ? void 0 : arazzo.workflows;
    if (!Array.isArray(workflows)) {
        return;
    }
    for (const [index, workflow] of workflows.entries()) {
        if (!(0, json_1.isPlainObject)(workflow)) {
            continue;
        }
        yield {
            path: ['workflows', index],
            workflow,
        };
    }
}
exports.getAllWorkflows = getAllWorkflows;
//# sourceMappingURL=getAllWorkflows.js.map
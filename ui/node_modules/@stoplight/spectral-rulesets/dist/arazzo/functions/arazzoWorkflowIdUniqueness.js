"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const getAllWorkflows_1 = require("./utils/getAllWorkflows");
function arazzoWorkflowIdUniqueness(targetVal, _options) {
    const results = [];
    const workflows = (0, getAllWorkflows_1.getAllWorkflows)(targetVal);
    const seenIds = new Set();
    for (const { path, workflow } of workflows) {
        const workflowId = workflow.workflowId;
        if (seenIds.has(workflowId)) {
            results.push({
                message: `"workflowId" must be unique across all workflows. "${workflowId}" is duplicated.`,
                path: [...path, 'workflowId'],
            });
        }
        else {
            seenIds.add(workflowId);
        }
    }
    return results;
}
exports.default = arazzoWorkflowIdUniqueness;
//# sourceMappingURL=arazzoWorkflowIdUniqueness.js.map
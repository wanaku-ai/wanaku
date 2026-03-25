"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const getAllWorkflows_1 = require("./utils/getAllWorkflows");
const arazzoRuntimeExpressionValidation_1 = (0, tslib_1.__importDefault)(require("./arazzoRuntimeExpressionValidation"));
function arazzoWorkflowDependsOnValidation(targetVal, _options) {
    var _a, _b;
    const results = [];
    const localWorkflowIds = new Set();
    const sourceDescriptionNames = new Map(((_a = targetVal.sourceDescriptions) !== null && _a !== void 0 ? _a : []).map(sd => [sd.name, sd.type]));
    const workflows = (_b = targetVal.workflows) !== null && _b !== void 0 ? _b : [];
    for (const { workflow } of (0, getAllWorkflows_1.getAllWorkflows)({ workflows })) {
        if ('workflowId' in workflow && typeof workflow.workflowId === 'string') {
            localWorkflowIds.add(workflow.workflowId);
        }
    }
    for (const { workflow, path } of (0, getAllWorkflows_1.getAllWorkflows)({ workflows })) {
        const seenWorkflows = new Set();
        if (Array.isArray(workflow.dependsOn)) {
            workflow.dependsOn.forEach((dep, depIndex) => {
                if (typeof dep !== 'string') {
                    return;
                }
                if (seenWorkflows.has(dep)) {
                    results.push({
                        message: `Duplicate workflowId "${dep}" in dependsOn for workflow "${workflow.workflowId}".`,
                        path: [...path, 'dependsOn', depIndex],
                    });
                    return;
                }
                else {
                    seenWorkflows.add(dep);
                }
                if (dep.startsWith('$')) {
                    if (!(0, arazzoRuntimeExpressionValidation_1.default)(dep, targetVal)) {
                        results.push({
                            message: `Runtime expression "${dep}" is invalid.`,
                            path: [...path, 'dependsOn', depIndex],
                        });
                    }
                }
                if (dep.startsWith('$sourceDescriptions.')) {
                    const parts = dep.split('.');
                    const sourceName = parts[1];
                    const workflowId = parts[2];
                    const sourceType = sourceDescriptionNames.get(sourceName);
                    if (sourceType == null) {
                        results.push({
                            message: `Source description "${sourceName}" not found for workflowId "${dep}".`,
                            path: [...path, 'dependsOn', depIndex],
                        });
                    }
                    else if (sourceType !== 'arazzo') {
                        results.push({
                            message: `Source description "${sourceName}" must have a type of "arazzo".`,
                            path: [...path, 'dependsOn', depIndex],
                        });
                    }
                    else if (workflowId == null) {
                        results.push({
                            message: `WorkflowId part is missing in the expression "${dep}".`,
                            path: [...path, 'dependsOn', depIndex],
                        });
                    }
                }
                else {
                    if (!localWorkflowIds.has(dep)) {
                        results.push({
                            message: `WorkflowId "${dep}" not found in local Arazzo workflows "${workflow.workflowId}".`,
                            path: [...path, 'dependsOn', depIndex],
                        });
                    }
                }
            });
        }
    }
    return results;
}
exports.default = arazzoWorkflowDependsOnValidation;
//# sourceMappingURL=arazzoWorkflowDependsOnValidation.js.map
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.applyUrlVariables = void 0;
function* applyUrlVariables(url, variables) {
    yield* _applyUrlVariables(url, 0, variables.map(toApplicableVariable));
}
exports.applyUrlVariables = applyUrlVariables;
function* _applyUrlVariables(url, i, variables) {
    const [name, values] = variables[i];
    let x = 0;
    while (x < values.length) {
        const substitutedValue = url.replace(name, values[x]);
        if (i === variables.length - 1) {
            yield substitutedValue;
        }
        else {
            yield* _applyUrlVariables(substitutedValue, i + 1, variables);
        }
        x++;
    }
}
function toApplicableVariable([name, values]) {
    return [toReplaceRegExp(name), values.map(encodeURI)];
}
function toReplaceRegExp(name) {
    return RegExp(escapeRegexp(`{${name}}`), 'g');
}
function escapeRegexp(value) {
    return value.replace(/[\\^$*+?.()|[\]{}]/g, '\\$&');
}
//# sourceMappingURL=applyUrlVariables.js.map
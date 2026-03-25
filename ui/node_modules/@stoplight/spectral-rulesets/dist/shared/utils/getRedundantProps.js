"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getRedundantProps = void 0;
function getRedundantProps(arr, keys) {
    return keys.filter(val => {
        return !arr.includes(val);
    });
}
exports.getRedundantProps = getRedundantProps;
//# sourceMappingURL=getRedundantProps.js.map
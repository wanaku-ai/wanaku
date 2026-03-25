"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getMissingProps = void 0;
function getMissingProps(arr, props) {
    return arr.filter(val => {
        return !props.includes(val);
    });
}
exports.getMissingProps = getMissingProps;
//# sourceMappingURL=getMissingProps.js.map
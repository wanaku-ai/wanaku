'use strict';

Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

function invariant(condition, message) {
    if (condition) {
        return;
    }
    throw new Error(message);
}

exports.invariant = invariant;

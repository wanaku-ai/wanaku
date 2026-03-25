'use strict';

Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

const unary = require('../_chunk/unary-c1NFA5.js');
const noop = require('../_chunk/noop-2IwLUk.js');

function before(n, func) {
    if (!Number.isInteger(n) || n < 0) {
        throw new Error('n must be a non-negative integer.');
    }
    let counter = 0;
    return (...args) => {
        if (++counter < n) {
            return func(...args);
        }
        return undefined;
    };
}

function curry(func) {
    if (func.length === 0 || func.length === 1) {
        return func;
    }
    return function (arg) {
        return makeCurry(func, func.length, [arg]);
    };
}
function makeCurry(origin, argsLength, args) {
    if (args.length === argsLength) {
        return origin(...args);
    }
    else {
        const next = function (arg) {
            return makeCurry(origin, argsLength, [...args, arg]);
        };
        return next;
    }
}

function curryRight(func) {
    if (func.length === 0 || func.length === 1) {
        return func;
    }
    return function (arg) {
        return makeCurryRight(func, func.length, [arg]);
    };
}
function makeCurryRight(origin, argsLength, args) {
    if (args.length === argsLength) {
        return origin(...args);
    }
    else {
        const next = function (arg) {
            return makeCurryRight(origin, argsLength, [arg, ...args]);
        };
        return next;
    }
}

function spread(func) {
    return function (argsArr) {
        return func.apply(this, argsArr);
    };
}

function throttle(func, throttleMs, { signal, edges = ['leading', 'trailing'] } = {}) {
    let pendingAt = null;
    const debounced = unary.debounce(func, throttleMs, { signal, edges });
    const throttled = function (...args) {
        if (pendingAt == null) {
            pendingAt = Date.now();
        }
        else {
            if (Date.now() - pendingAt >= throttleMs) {
                pendingAt = Date.now();
                debounced.cancel();
                debounced(...args);
            }
        }
        debounced(...args);
    };
    throttled.cancel = debounced.cancel;
    throttled.flush = debounced.flush;
    return throttled;
}

exports.after = unary.after;
exports.ary = unary.ary;
exports.asyncNoop = unary.asyncNoop;
exports.debounce = unary.debounce;
exports.flow = unary.flow;
exports.flowRight = unary.flowRight;
exports.identity = unary.identity;
exports.memoize = unary.memoize;
exports.negate = unary.negate;
exports.once = unary.once;
exports.partial = unary.partial;
exports.partialRight = unary.partialRight;
exports.rest = unary.rest;
exports.retry = unary.retry;
exports.unary = unary.unary;
exports.noop = noop.noop;
exports.before = before;
exports.curry = curry;
exports.curryRight = curryRight;
exports.spread = spread;
exports.throttle = throttle;

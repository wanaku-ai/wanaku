'use strict';

function clamp(value, bound1, bound2) {
    if (bound2 == null) {
        return Math.min(value, bound1);
    }
    return Math.min(Math.max(value, bound1), bound2);
}

function inRange(value, minimum, maximum) {
    if (maximum == null) {
        maximum = minimum;
        minimum = 0;
    }
    if (minimum >= maximum) {
        throw new Error('The maximum value must be greater than the minimum value.');
    }
    return minimum <= value && value < maximum;
}

function sum(nums) {
    let result = 0;
    for (let i = 0; i < nums.length; i++) {
        result += nums[i];
    }
    return result;
}

function mean(nums) {
    return sum(nums) / nums.length;
}

function meanBy(items, getValue) {
    const nums = items.map(x => getValue(x));
    return mean(nums);
}

function median(nums) {
    if (nums.length === 0) {
        return NaN;
    }
    const sorted = nums.slice().sort((a, b) => a - b);
    const middleIndex = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 0) {
        return (sorted[middleIndex - 1] + sorted[middleIndex]) / 2;
    }
    else {
        return sorted[middleIndex];
    }
}

function medianBy(items, getValue) {
    const nums = items.map(x => getValue(x));
    return median(nums);
}

function range(start, end, step = 1) {
    if (end == null) {
        end = start;
        start = 0;
    }
    if (!Number.isInteger(step) || step === 0) {
        throw new Error(`The step value must be a non-zero integer.`);
    }
    const length = Math.max(Math.ceil((end - start) / step), 0);
    const result = new Array(length);
    for (let i = 0; i < length; i++) {
        result[i] = start + i * step;
    }
    return result;
}

exports.clamp = clamp;
exports.inRange = inRange;
exports.mean = mean;
exports.meanBy = meanBy;
exports.median = median;
exports.medianBy = medianBy;
exports.range = range;
exports.sum = sum;

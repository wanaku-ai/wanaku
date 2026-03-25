'use strict';

Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

const zipWith = require('../_chunk/zipWith-Bdyzuy.js');

function compareValues(a, b, order) {
    if (a < b) {
        return order === 'asc' ? -1 : 1;
    }
    if (a > b) {
        return order === 'asc' ? 1 : -1;
    }
    return 0;
}

function orderBy(arr, criteria, orders) {
    return arr.slice().sort((a, b) => {
        const ordersLength = orders.length;
        for (let i = 0; i < criteria.length; i++) {
            const order = ordersLength > i ? orders[i] : orders[ordersLength - 1];
            const criterion = criteria[i];
            const criterionIsFunction = typeof criterion === 'function';
            const valueA = criterionIsFunction ? criterion(a) : a[criterion];
            const valueB = criterionIsFunction ? criterion(b) : b[criterion];
            const result = compareValues(valueA, valueB, order);
            if (result !== 0) {
                return result;
            }
        }
        return 0;
    });
}

function sortBy(arr, criteria) {
    return orderBy(arr, criteria, ['asc']);
}

function takeRightWhile(arr, shouldContinueTaking) {
    for (let i = arr.length - 1; i >= 0; i--) {
        if (!shouldContinueTaking(arr[i])) {
            return arr.slice(i + 1);
        }
    }
    return arr.slice();
}

exports.at = zipWith.at;
exports.chunk = zipWith.chunk;
exports.compact = zipWith.compact;
exports.countBy = zipWith.countBy;
exports.difference = zipWith.difference;
exports.differenceBy = zipWith.differenceBy;
exports.differenceWith = zipWith.differenceWith;
exports.drop = zipWith.drop;
exports.dropRight = zipWith.dropRight;
exports.dropRightWhile = zipWith.dropRightWhile;
exports.dropWhile = zipWith.dropWhile;
exports.fill = zipWith.fill;
exports.flatMap = zipWith.flatMap;
exports.flatMapDeep = zipWith.flatMapDeep;
exports.flatten = zipWith.flatten;
exports.flattenDeep = zipWith.flattenDeep;
exports.forEachRight = zipWith.forEachRight;
exports.groupBy = zipWith.groupBy;
exports.head = zipWith.head;
exports.initial = zipWith.initial;
exports.intersection = zipWith.intersection;
exports.intersectionBy = zipWith.intersectionBy;
exports.intersectionWith = zipWith.intersectionWith;
exports.isSubset = zipWith.isSubset;
exports.isSubsetWith = zipWith.isSubsetWith;
exports.keyBy = zipWith.keyBy;
exports.last = zipWith.last;
exports.maxBy = zipWith.maxBy;
exports.minBy = zipWith.minBy;
exports.partition = zipWith.partition;
exports.pull = zipWith.pull;
exports.pullAt = zipWith.pullAt;
exports.remove = zipWith.remove;
exports.sample = zipWith.sample;
exports.sampleSize = zipWith.sampleSize;
exports.shuffle = zipWith.shuffle;
exports.tail = zipWith.tail;
exports.take = zipWith.take;
exports.takeRight = zipWith.takeRight;
exports.takeWhile = zipWith.takeWhile;
exports.toFilled = zipWith.toFilled;
exports.union = zipWith.union;
exports.unionBy = zipWith.unionBy;
exports.unionWith = zipWith.unionWith;
exports.uniq = zipWith.uniq;
exports.uniqBy = zipWith.uniqBy;
exports.uniqWith = zipWith.uniqWith;
exports.unzip = zipWith.unzip;
exports.unzipWith = zipWith.unzipWith;
exports.windowed = zipWith.windowed;
exports.without = zipWith.without;
exports.xor = zipWith.xor;
exports.xorBy = zipWith.xorBy;
exports.xorWith = zipWith.xorWith;
exports.zip = zipWith.zip;
exports.zipObject = zipWith.zipObject;
exports.zipWith = zipWith.zipWith;
exports.orderBy = orderBy;
exports.sortBy = sortBy;
exports.takeRightWhile = takeRightWhile;

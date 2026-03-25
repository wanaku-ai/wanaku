import { identity } from '../../function/identity.mjs';
import { property } from '../object/property.mjs';
import { isArray } from '../predicate/isArray.mjs';
import { matches } from '../predicate/matches.mjs';
import { matchesProperty } from '../predicate/matchesProperty.mjs';

function filter(source, predicate) {
    if (!source) {
        return [];
    }
    if (!predicate) {
        predicate = identity;
    }
    const collection = isArray(source) ? source : Object.values(source);
    switch (typeof predicate) {
        case 'function': {
            if (!Array.isArray(source)) {
                const result = [];
                const keys = Object.keys(source);
                for (let i = 0; i < keys.length; i++) {
                    const key = keys[i];
                    const value = source[key];
                    if (predicate(value, key, source)) {
                        result.push(value);
                    }
                }
                return result;
            }
            return collection.filter(predicate);
        }
        case 'object': {
            return isArray(predicate)
                ? collection.filter(matchesProperty(predicate[0], predicate[1]))
                : collection.filter(matches(predicate));
        }
        case 'symbol':
        case 'number':
        case 'string': {
            return collection.filter(property(predicate));
        }
    }
}

export { filter };

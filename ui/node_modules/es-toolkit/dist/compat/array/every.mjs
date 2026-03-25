import { identity } from '../../function/identity.mjs';
import { isIterateeCall } from '../_internal/isIterateeCall.mjs';
import { property } from '../object/property.mjs';
import { matches } from '../predicate/matches.mjs';
import { matchesProperty } from '../predicate/matchesProperty.mjs';

function every(source, doesMatch, guard) {
    if (!source) {
        return true;
    }
    const values = Array.isArray(source) ? source : Object.values(source);
    if (guard && isIterateeCall(source, doesMatch, guard)) {
        doesMatch = undefined;
    }
    if (!doesMatch) {
        doesMatch = identity;
    }
    switch (typeof doesMatch) {
        case 'function': {
            if (!Array.isArray(source)) {
                const keys = Object.keys(source);
                for (let i = 0; i < keys.length; i++) {
                    const key = keys[i];
                    const value = source[key];
                    if (!doesMatch(value, key, source)) {
                        return false;
                    }
                }
                return true;
            }
            return values.every(doesMatch);
        }
        case 'object': {
            if (Array.isArray(doesMatch) && doesMatch.length === 2) {
                const key = doesMatch[0];
                const value = doesMatch[1];
                return values.every(matchesProperty(key, value));
            }
            else {
                return values.every(matches(doesMatch));
            }
        }
        case 'symbol':
        case 'number':
        case 'string': {
            return values.every(property(doesMatch));
        }
    }
}

export { every };

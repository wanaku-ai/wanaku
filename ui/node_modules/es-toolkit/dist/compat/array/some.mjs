import { identity } from '../../function/identity.mjs';
import { property } from '../object/property.mjs';
import { matches } from '../predicate/matches.mjs';
import { matchesProperty } from '../predicate/matchesProperty.mjs';

function some(source, predicate, guard) {
    if (!source) {
        return false;
    }
    if (guard != null) {
        predicate = undefined;
    }
    if (!predicate) {
        predicate = identity;
    }
    const values = Array.isArray(source) ? source : Object.values(source);
    switch (typeof predicate) {
        case 'function': {
            if (!Array.isArray(source)) {
                const keys = Object.keys(source);
                for (let i = 0; i < keys.length; i++) {
                    const key = keys[i];
                    const value = source[key];
                    if (predicate(value, key, source)) {
                        return true;
                    }
                }
                return false;
            }
            return values.some(predicate);
        }
        case 'object': {
            if (Array.isArray(predicate) && predicate.length === 2) {
                const key = predicate[0];
                const value = predicate[1];
                return values.some(matchesProperty(key, value));
            }
            else {
                return values.some(matches(predicate));
            }
        }
        case 'number':
        case 'symbol':
        case 'string': {
            return values.some(property(predicate));
        }
    }
}

export { some };

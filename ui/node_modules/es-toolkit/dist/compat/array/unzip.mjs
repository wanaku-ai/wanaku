import { unzip as unzip$1 } from '../../array/unzip.mjs';
import { isArrayLikeObject } from '../predicate/isArrayLikeObject.mjs';

function unzip(array) {
    if (!isArrayLikeObject(array) || !array.length) {
        return [];
    }
    if (Array.isArray(array)) {
        return unzip$1(array);
    }
    return unzip$1(Array.from(array, value => Array.from(value)));
}

export { unzip };

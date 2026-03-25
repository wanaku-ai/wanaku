import { iteratee } from '../util/iteratee.mjs';

function sumBy(array, iteratee$1) {
    if (!array || !array.length) {
        return 0;
    }
    if (iteratee$1 != null) {
        iteratee$1 = iteratee(iteratee$1);
    }
    let result = iteratee$1 ? iteratee$1(array[0]) : array[0];
    for (let i = 1; i < array.length; i++) {
        const current = iteratee$1 ? iteratee$1(array[i]) : array[i];
        if (current !== undefined) {
            result += current;
        }
    }
    return result;
}

export { sumBy };

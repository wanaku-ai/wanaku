import { CASE_SPLIT_PATTERN } from '../../string/words.mjs';
import { toString } from '../util/toString.mjs';

function words(str, pattern = CASE_SPLIT_PATTERN) {
    const input = toString(str);
    const words = Array.from(input.match(pattern) ?? []);
    return words.filter(x => x !== '');
}

export { words };

import { eq } from '../util/eq.mjs';

function defaults(object, ...sources) {
    object = Object(object);
    const objectProto = Object.prototype;
    for (let i = 0; i < sources.length; i++) {
        const source = sources[i];
        const keys = Object.keys(source);
        for (let j = 0; j < keys.length; j++) {
            const key = keys[j];
            const value = object[key];
            if (value === undefined ||
                (!Object.hasOwn(object, key) && eq(value, objectProto[key]))) {
                object[key] = source[key];
            }
        }
    }
    return object;
}

export { defaults };

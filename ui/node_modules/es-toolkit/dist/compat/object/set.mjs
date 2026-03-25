import { isIndex } from '../_internal/isIndex.mjs';
import { toPath } from '../util/toPath.mjs';

function set(obj, path, value) {
    const resolvedPath = Array.isArray(path) ? path : typeof path === 'string' ? toPath(path) : [path];
    let current = obj;
    for (let i = 0; i < resolvedPath.length - 1; i++) {
        const key = resolvedPath[i];
        const nextKey = resolvedPath[i + 1];
        if (current[key] == null) {
            current[key] = isIndex(nextKey) ? [] : {};
        }
        current = current[key];
    }
    const lastKey = resolvedPath[resolvedPath.length - 1];
    current[lastKey] = value;
    return obj;
}

export { set };

/**
 * Finds the element in an array that has the maximum value.
 *
 * @param {[T, ...T[]]} items - The array of elements to search.
 * @returns {T | undefined} - The element with the maximum value, or undefined if the array is empty.
 * @example
 * // Returns 9
 * max([3, 1, 4, 1, 5, 9]);
 *
 * @example
 * // Returns 8
 * max([0, -3, 2, 8, 7]);
 */
declare function max<T>(items: readonly [T, ...T[]]): T;
/**
 * Finds the element in an array that has the maximum value.
 * Returns undefined when no arguments are provided.
 * @returns {undefined}
 */
declare function max(): undefined;
/**
 * Finds the element in an array that has the maximum value.
 *
 * @template T - The type of elements in the array.
 * @param {T[]} [items] - The array of elements to search. Defaults to an empty array.
 * @returns {T | undefined} - The element with the maximum value, or undefined if the array is empty.
 */
declare function max<T>(items?: readonly T[]): T | undefined;

export { max };

function partialRight(func, ...partialArgs) {
    return function (...providedArgs) {
        const placeholderLength = partialArgs.filter(arg => arg === partialRightPlaceholder).length;
        const rangeLength = Math.max(providedArgs.length - placeholderLength, 0);
        const args = [];
        let providedIndex = 0;
        for (let i = 0; i < rangeLength; i++) {
            args.push(providedArgs[providedIndex++]);
        }
        for (let i = 0; i < partialArgs.length; i++) {
            const arg = partialArgs[i];
            if (arg === partialRight.placeholder) {
                args.push(providedArgs[providedIndex++]);
            }
            else {
                args.push(arg);
            }
        }
        return func.apply(this, args);
    };
}
const partialRightPlaceholder = Symbol('partialRight.placeholder');
partialRight.placeholder = partialRightPlaceholder;

export { partialRight };

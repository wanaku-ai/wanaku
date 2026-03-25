function partial(func, ...partialArgs) {
    return function (...providedArgs) {
        const args = [];
        let startIndex = 0;
        for (let i = 0; i < partialArgs.length; i++) {
            const arg = partialArgs[i];
            if (arg === partial.placeholder) {
                args.push(providedArgs[startIndex++]);
            }
            else {
                args.push(arg);
            }
        }
        for (let i = startIndex; i < providedArgs.length; i++) {
            args.push(providedArgs[i]);
        }
        return func.apply(this, args);
    };
}
const partialPlaceholder = Symbol('partial.placeholder');
partial.placeholder = partialPlaceholder;

export { partial };

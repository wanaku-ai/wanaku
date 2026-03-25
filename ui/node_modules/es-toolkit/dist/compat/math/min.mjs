function min(items = []) {
    let minElement = items[0];
    let min = undefined;
    for (let i = 0; i < items.length; i++) {
        const element = items[i];
        if (min == null || element < min) {
            min = element;
            minElement = element;
        }
    }
    return minElement;
}

export { min };

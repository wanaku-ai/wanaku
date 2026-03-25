function minBy(items, getValue) {
    let minElement = items[0];
    let min = Infinity;
    for (let i = 0; i < items.length; i++) {
        const element = items[i];
        const value = getValue(element);
        if (value < min) {
            min = value;
            minElement = element;
        }
    }
    return minElement;
}

export { minBy };

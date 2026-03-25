function maxBy(items, getValue) {
    let maxElement = items[0];
    let max = -Infinity;
    for (let i = 0; i < items.length; i++) {
        const element = items[i];
        const value = getValue(element);
        if (value > max) {
            max = value;
            maxElement = element;
        }
    }
    return maxElement;
}

export { maxBy };

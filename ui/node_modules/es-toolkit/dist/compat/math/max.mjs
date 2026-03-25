function max(items = []) {
    let maxElement = items[0];
    let max = undefined;
    for (let i = 0; i < items.length; i++) {
        const element = items[i];
        if (max == null || element > max) {
            max = element;
            maxElement = element;
        }
    }
    return maxElement;
}

export { max };

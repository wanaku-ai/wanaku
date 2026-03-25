function takeRight(arr, count = 1) {
    if (count <= 0) {
        return [];
    }
    return arr.slice(-count);
}

export { takeRight };

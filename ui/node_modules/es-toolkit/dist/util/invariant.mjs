function invariant(condition, message) {
    if (condition) {
        return;
    }
    throw new Error(message);
}

export { invariant };

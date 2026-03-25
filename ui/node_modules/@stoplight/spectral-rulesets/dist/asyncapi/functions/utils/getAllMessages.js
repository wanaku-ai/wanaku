"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getAllMessages = void 0;
const json_1 = require("@stoplight/json");
const getAllOperations_1 = require("./getAllOperations");
function* getAllMessages(asyncapi) {
    for (const { path, operation } of (0, getAllOperations_1.getAllOperations)(asyncapi)) {
        if (!(0, json_1.isPlainObject)(operation)) {
            continue;
        }
        const maybeMessage = operation.message;
        if (!(0, json_1.isPlainObject)(maybeMessage)) {
            continue;
        }
        if (Array.isArray(maybeMessage.oneOf)) {
            for (const [index, message] of maybeMessage.oneOf.entries()) {
                if ((0, json_1.isPlainObject)(message)) {
                    yield {
                        path: [...path, 'message', 'oneOf', index],
                        message,
                    };
                }
            }
        }
        else {
            yield {
                path: [...path, 'message'],
                message: maybeMessage,
            };
        }
    }
}
exports.getAllMessages = getAllMessages;
//# sourceMappingURL=getAllMessages.js.map
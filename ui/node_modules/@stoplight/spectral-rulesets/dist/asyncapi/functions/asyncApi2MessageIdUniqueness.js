"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const spectral_core_1 = require("@stoplight/spectral-core");
const json_1 = require("@stoplight/json");
const getAllMessages_1 = require("./utils/getAllMessages");
function retrieveMessageId(message) {
    if (Array.isArray(message.traits)) {
        for (let i = message.traits.length - 1; i >= 0; i--) {
            const trait = message.traits[i];
            if ((0, json_1.isPlainObject)(trait) && typeof trait.messageId === 'string') {
                return {
                    messageId: trait.messageId,
                    path: ['traits', i, 'messageId'],
                };
            }
        }
    }
    if (typeof message.messageId === 'string') {
        return {
            messageId: message.messageId,
            path: ['messageId'],
        };
    }
    return undefined;
}
exports.default = (0, spectral_core_1.createRulesetFunction)({
    input: {
        type: 'object',
        properties: {
            channels: {
                type: 'object',
                properties: {
                    subscribe: {
                        type: 'object',
                        properties: {
                            message: {
                                oneOf: [
                                    { type: 'object' },
                                    {
                                        type: 'object',
                                        properties: {
                                            oneOf: {
                                                type: 'array',
                                            },
                                        },
                                    },
                                ],
                            },
                        },
                    },
                    publish: {
                        type: 'object',
                        properties: {
                            message: {
                                oneOf: [
                                    { type: 'object' },
                                    {
                                        type: 'object',
                                        properties: {
                                            oneOf: {
                                                type: 'array',
                                            },
                                        },
                                    },
                                ],
                            },
                        },
                    },
                },
            },
        },
    },
    options: null,
}, function asyncApi2MessageIdUniqueness(targetVal, _) {
    const results = [];
    const messages = (0, getAllMessages_1.getAllMessages)(targetVal);
    const seenIds = [];
    for (const { path, message } of messages) {
        const maybeMessageId = retrieveMessageId(message);
        if (maybeMessageId === undefined) {
            continue;
        }
        if (seenIds.includes(maybeMessageId.messageId)) {
            results.push({
                message: '"messageId" must be unique across all the messages.',
                path: [...path, ...maybeMessageId.path],
            });
        }
        else {
            seenIds.push(maybeMessageId.messageId);
        }
    }
    return results;
});
//# sourceMappingURL=asyncApi2MessageIdUniqueness.js.map
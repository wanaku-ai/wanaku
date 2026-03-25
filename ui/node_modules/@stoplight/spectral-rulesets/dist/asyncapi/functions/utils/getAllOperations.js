"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getAllOperations = void 0;
const json_1 = require("@stoplight/json");
function* getAllOperations(asyncapi) {
    const channels = asyncapi === null || asyncapi === void 0 ? void 0 : asyncapi.channels;
    if (!(0, json_1.isPlainObject)(channels)) {
        return {};
    }
    for (const [channelAddress, channel] of Object.entries(channels)) {
        if (!(0, json_1.isPlainObject)(channel)) {
            continue;
        }
        if ((0, json_1.isPlainObject)(channel.subscribe)) {
            yield {
                path: ['channels', channelAddress, 'subscribe'],
                kind: 'subscribe',
                operation: channel.subscribe,
            };
        }
        if ((0, json_1.isPlainObject)(channel.publish)) {
            yield {
                path: ['channels', channelAddress, 'publish'],
                kind: 'publish',
                operation: channel.publish,
            };
        }
    }
}
exports.getAllOperations = getAllOperations;
//# sourceMappingURL=getAllOperations.js.map
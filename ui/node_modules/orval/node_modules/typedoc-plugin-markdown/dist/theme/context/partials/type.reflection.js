import { backTicks } from '../../../libs/markdown/index.js';
import { ReflectionType } from 'typedoc';
export function reflectionType(model, options) {
    const root = model instanceof ReflectionType ? model.declaration : model;
    if (root.signatures) {
        return this.partials.functionType(root.signatures);
    }
    const expandObjects = options?.forceCollapse || this.options.getValue('expandObjects');
    return expandObjects
        ? this.partials.declarationType(root, options)
        : backTicks('object');
}

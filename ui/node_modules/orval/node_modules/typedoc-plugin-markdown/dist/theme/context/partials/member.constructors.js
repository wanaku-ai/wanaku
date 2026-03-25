import { heading } from '../../../libs/markdown/index.js';
import { escapeChars } from '../../../libs/utils/escape-chars.js';
export function constructor(model, options) {
    const md = [];
    model.signatures?.forEach((signature) => {
        md.push(heading(options.headingLevel, `new ${escapeChars(signature.name)}()`));
        md.push(this.partials.signature(signature, {
            headingLevel: options.headingLevel + 1,
        }));
    });
    return md.join('\n\n');
}

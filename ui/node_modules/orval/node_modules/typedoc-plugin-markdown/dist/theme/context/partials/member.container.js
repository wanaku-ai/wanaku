import { heading } from '../../../libs/markdown/index.js';
import { ReflectionKind } from 'typedoc';
export function memberContainer(model, options) {
    const md = [];
    if (!model.hasOwnDocument &&
        model.url &&
        this.options.getValue('useHTMLAnchors')) {
        md.push(`<a id="${model.anchor}"></a>`);
    }
    if (!model.hasOwnDocument &&
        ![ReflectionKind.Constructor].includes(model.kind)) {
        md.push(heading(options.headingLevel, this.partials.memberTitle(model)));
    }
    md.push(this.partials.member(model, {
        headingLevel: options.headingLevel,
        nested: options.nested,
    }));
    return md.join('\n\n');
}

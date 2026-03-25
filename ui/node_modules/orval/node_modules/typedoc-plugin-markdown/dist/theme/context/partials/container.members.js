import { horizontalRule } from '../../../libs/markdown/index.js';
export function members(model, options) {
    const md = [];
    const displayHr = (reflection) => {
        if (this.options.getValue('outputFileStrategy') === 'modules') {
            return this.helpers.isGroupKind(reflection);
        }
        return true;
    };
    const items = model?.filter((item) => !item.hasOwnDocument);
    items?.forEach((item, index) => {
        md.push(this.partials.memberContainer(item, {
            headingLevel: options.headingLevel,
            groupTitle: options.groupTitle,
        }));
        if (index < items.length - 1 && displayHr(item)) {
            md.push(horizontalRule());
        }
    });
    return md.join('\n\n');
}

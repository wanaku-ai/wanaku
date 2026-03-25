import { heading, unorderedList } from '../../../libs/markdown/index.js';
export function hierarchy(model, options) {
    const md = [];
    const getHierarchy = (hModel) => {
        const parent = !hModel.isTarget
            ? hModel.types
                .map((hierarchyType) => {
                return this.helpers.getHierarchyType(hierarchyType, {
                    isTarget: hModel.isTarget || false,
                });
            })
                .join('.')
            : null;
        if (hModel.next) {
            if (parent) {
                md.push(heading(options.headingLevel, this.i18n.theme_extends()));
                md.push(`- ${parent}`);
            }
            else {
                md.push(heading(options.headingLevel, this.i18n.theme_extended_by()));
                const lines = [];
                hModel.next.types.forEach((hierarchyType) => {
                    lines.push(this.helpers.getHierarchyType(hierarchyType, {
                        isTarget: hModel.next?.isTarget || false,
                    }));
                });
                md.push(unorderedList(lines));
            }
            if (hModel.next?.next) {
                getHierarchy(hModel.next);
            }
        }
    };
    getHierarchy(model);
    return md.join('\n\n');
}

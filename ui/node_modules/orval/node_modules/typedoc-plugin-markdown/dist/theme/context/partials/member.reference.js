import { link } from '../../../libs/markdown/index.js';
import { ReflectionKind } from 'typedoc';
export function referenceMember(model) {
    let referenced = model.tryGetTargetReflectionDeep();
    const reExportsText = this.i18n.theme_re_exports();
    const renamesAndReExportsText = this.i18n.theme_renames_and_re_exports();
    if (!referenced) {
        return `${reExportsText} ${model.name}`;
    }
    if (referenced?.kind === ReflectionKind.TypeLiteral && referenced.parent) {
        referenced = referenced?.parent;
    }
    if (!referenced?.url) {
        return `${reExportsText} ${referenced.name}`;
    }
    if (model.name === referenced.name) {
        return `${reExportsText} ${link(referenced.name, this.getRelativeUrl(referenced.url))}`;
    }
    return `${renamesAndReExportsText} ${link(referenced.name, this.getRelativeUrl(referenced.url))}`;
}

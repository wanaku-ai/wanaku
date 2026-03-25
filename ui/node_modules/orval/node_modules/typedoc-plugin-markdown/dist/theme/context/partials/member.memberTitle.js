import { backTicks, strikeThrough } from '../../../libs/markdown/index.js';
import { encodeAngleBrackets, escapeChars } from '../../../libs/utils/index.js';
import { ReflectionKind } from 'typedoc';
export function memberTitle(model) {
    const md = [];
    const name = [];
    if (model?.kind === ReflectionKind.Class && model.flags?.isAbstract) {
        name.push(this.helpers.getReflectionFlags(model.flags) + ' ');
    }
    const modelName = this.options.getValue('useHTMLEncodedBrackets')
        ? encodeAngleBrackets(model.name)
        : model.name;
    name.push(`${/\\/.test(model.name) ? backTicks(model.name) : escapeChars(modelName)}`);
    if (model.signatures?.length ||
        model.type?.declaration?.signatures?.length) {
        name.push('()');
    }
    if (model.typeParameters?.length) {
        const typeParameters = model.typeParameters
            .map((typeParameter) => typeParameter.name)
            .join(', ');
        name.push(`${`${this.helpers.getAngleBracket('<')}${typeParameters}${this.helpers.getAngleBracket('>')}`}`);
    }
    if (model.flags.isOptional) {
        name.push('?');
    }
    if (model.isDeprecated && model.isDeprecated()) {
        md.push(strikeThrough(name.join('')));
    }
    else {
        md.push(name.join(''));
    }
    return md.join(': ');
}

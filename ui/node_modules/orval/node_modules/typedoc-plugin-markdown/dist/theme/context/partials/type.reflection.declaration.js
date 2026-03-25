import { backTicks } from '../../../libs/markdown/index.js';
export function declarationType(model, options) {
    const shouldFormat = this.options.getValue('useCodeBlocks');
    if (model.indexSignatures || model.children) {
        const indexSignatureMd = [];
        if (model.indexSignatures?.length) {
            model.indexSignatures.forEach((indexSignature) => {
                const key = indexSignature.parameters
                    ? indexSignature.parameters.map((param) => `\`[${param.name}: ${param.type}]\``)
                    : '';
                const obj = this.partials.someType(indexSignature.type);
                indexSignatureMd.push(`${key}: ${obj}; `);
            });
        }
        const children = model.children;
        const types = children &&
            children.map((obj) => {
                const name = [];
                if (obj.getSignature || Boolean(obj.setSignature)) {
                    if (obj.getSignature) {
                        name.push('get');
                    }
                    if (obj.setSignature) {
                        name.push('set');
                    }
                }
                name.push(backTicks(obj.name));
                const theType = this.helpers.getDeclarationType(obj);
                const typeString = this.partials.someType(theType, options);
                if (shouldFormat) {
                    return `  ${name.join(' ')}: ${indentBlock(typeString)};\n`;
                }
                return `${name.join(' ')}: ${indentBlock(typeString)};`;
            });
        if (indexSignatureMd) {
            indexSignatureMd.forEach((indexSignature) => {
                types?.unshift(indexSignature);
            });
        }
        return types
            ? `\\{${shouldFormat ? `\n${types.join('')}` : ` ${types.join(' ')}`} \\}`
            : '\\{\\}';
    }
    return '\\{\\}';
}
function indentBlock(content) {
    const lines = content.split(`${'\n'}`);
    return lines
        .filter((line) => Boolean(line.length))
        .map((line, i) => {
        if (i === 0) {
            return line;
        }
        if (i === lines.length - 1) {
            return line.trim().startsWith('}') ? line : `   ${line}`;
        }
        return `   ${line}`;
    })
        .join(`${`\n`}`);
}

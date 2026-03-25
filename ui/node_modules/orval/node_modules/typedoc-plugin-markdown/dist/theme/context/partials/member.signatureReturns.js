import { backTicks, heading } from '../../../libs/markdown/index.js';
export function signatureReturns(model, options) {
    const md = [];
    const typeDeclaration = model.type
        ?.declaration;
    md.push(heading(options.headingLevel, this.i18n.theme_returns()));
    if (typeDeclaration?.signatures) {
        md.push(backTicks('Function'));
    }
    else {
        md.push(this.helpers.getReturnType(model.type));
    }
    if (model.comment?.blockTags.length) {
        const tags = model.comment.blockTags
            .filter((tag) => tag.tag === '@returns')
            .map((tag) => this.helpers.getCommentParts(tag.content));
        md.push(tags.join('\n\n'));
    }
    if (typeDeclaration?.signatures) {
        typeDeclaration.signatures.forEach((signature) => {
            md.push(this.partials.signature(signature, {
                headingLevel: options.headingLevel + 1,
                nested: true,
            }));
        });
    }
    if (typeDeclaration?.children) {
        md.push(this.partials.typeDeclaration(typeDeclaration, {
            headingLevel: options.headingLevel,
        }));
    }
    return md.join('\n\n');
}

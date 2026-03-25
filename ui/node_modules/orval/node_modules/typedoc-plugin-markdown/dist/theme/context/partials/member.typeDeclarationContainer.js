import { heading } from '../../../libs/markdown/index.js';
import { ReflectionKind } from 'typedoc';
export function typeDeclarationContainer(model, typeDeclaration, opts) {
    const md = [];
    if (typeDeclaration?.indexSignatures?.length) {
        md.push(heading(opts.headingLevel, this.i18n.kind_index_signature()));
        typeDeclaration?.indexSignatures?.forEach((indexSignature) => {
            md.push(this.partials.indexSignature(indexSignature));
        });
    }
    if (typeDeclaration?.signatures?.length) {
        typeDeclaration.signatures.forEach((signature) => {
            md.push(this.partials.signature(signature, {
                headingLevel: opts.headingLevel,
                nested: true,
            }));
        });
    }
    if (typeDeclaration?.children?.length) {
        const useHeading = model.kind !== ReflectionKind.Property ||
            this.helpers.useTableFormat('properties');
        if (!opts.nested && typeDeclaration?.children?.length) {
            if (typeDeclaration.categories) {
                typeDeclaration.categories.forEach((category) => {
                    md.push(heading(opts.headingLevel, category.title));
                    md.push(this.partials.typeDeclaration(category, {
                        headingLevel: useHeading
                            ? opts.headingLevel + 1
                            : opts.headingLevel,
                    }));
                });
            }
            else {
                md.push(this.partials.typeDeclaration(typeDeclaration, {
                    headingLevel: useHeading
                        ? opts.headingLevel
                        : opts.headingLevel - 1,
                }));
            }
        }
    }
    return md.join('\n\n');
}

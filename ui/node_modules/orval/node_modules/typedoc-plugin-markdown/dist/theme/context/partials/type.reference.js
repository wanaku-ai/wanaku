import { backTicks, link } from '../../../libs/markdown/index.js';
export function referenceType(model) {
    if (model.reflection || (model.name && model.typeArguments)) {
        const reflection = [];
        if (model.reflection?.url) {
            reflection.push(link(backTicks(model.reflection.name), this.getRelativeUrl(model.reflection.url)));
        }
        else {
            reflection.push(model.externalUrl
                ? link(backTicks(model.name), model.externalUrl)
                : backTicks(model.name));
        }
        if (model.typeArguments && model.typeArguments.length) {
            reflection.push(this.partials.typeArguments(model.typeArguments, {
                forceCollapse: true,
            }));
        }
        return reflection.join('');
    }
    return model.externalUrl
        ? link(backTicks(model.name), model.externalUrl)
        : backTicks(model.name);
}

import { backTicks } from '../../../libs/markdown/index.js';
export function functionType(model, options) {
    const functions = model.map((fn) => {
        const typeParams = fn.typeParameters
            ? `${this.helpers.getAngleBracket('<')}${fn.typeParameters
                .map((typeParameter) => backTicks(typeParameter.name))
                .join(', ')}${this.helpers.getAngleBracket('>')}`
            : [];
        const showParameterType = options?.forceParameterType || this.options.getValue('expandParameters');
        const params = fn.parameters
            ? fn.parameters.map((param) => {
                const paramType = this.partials.someType(param.type);
                const paramItem = [
                    `${param.flags.isRest ? '...' : ''}${backTicks(param.name)}${param.flags.isOptional ? '?' : ''}`,
                ];
                if (showParameterType) {
                    paramItem.push(paramType);
                }
                return paramItem.join(': ');
            })
            : [];
        const returns = this.partials.someType(fn.type);
        return typeParams + `(${params.join(', ')}) => ${returns}`;
    });
    return functions.join('');
}

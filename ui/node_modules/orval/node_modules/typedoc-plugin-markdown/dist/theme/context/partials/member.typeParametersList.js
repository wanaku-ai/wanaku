import { bold, italic } from '../../../libs/markdown/index.js';
export function typeParametersList(model) {
    const rows = [];
    model?.forEach((typeParameter) => {
        const row = [];
        const nameCol = [bold(typeParameter.name)];
        if (typeParameter.type) {
            nameCol.push(`${italic('extends')} ${this.partials.someType(typeParameter.type)}`);
        }
        if (typeParameter.default) {
            nameCol.push(`= ${this.partials.someType(typeParameter.default, { forceCollapse: true })}`);
        }
        row.push('• ' + nameCol.join(' '));
        if (typeParameter.comment) {
            row.push(this.partials.comment(typeParameter.comment));
        }
        rows.push(row.join('\n\n'));
    });
    return rows.join('\n\n');
}

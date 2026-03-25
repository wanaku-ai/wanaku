import { bold, link } from '../../../libs/markdown/index.js';
import { removeFirstScopedDirectory } from '../../../libs/utils/index.js';
import * as path from 'path';
import { EntryPointStrategy, ReflectionKind, } from 'typedoc';
export function header() {
    const textContentMappings = this.options.getValue('textContentMappings');
    const getHeader = () => {
        const isPackages = this.options.getValue('entryPointStrategy') ===
            EntryPointStrategy.Packages;
        if (isPackages) {
            const packageItem = findPackage(this.page.model);
            if (packageItem) {
                return getPackageHeader();
            }
        }
        return getProjectHeader();
    };
    const getProjectHeader = () => {
        const fileExtension = this.options.getValue('fileExtension');
        const entryFileName = `${path.parse(this.options.getValue('entryFileName')).name}${fileExtension}`;
        const md = [];
        const title = this.helpers.getProjectName(textContentMappings['header.title'], this.page);
        if (this.page.url === entryFileName) {
            md.push(bold(title));
        }
        else {
            md.push(link(bold(title), this.getRelativeUrl(entryFileName)));
        }
        return `${md.join(' • ')}\n\n***\n`;
    };
    const getPackageHeader = () => {
        const packageItem = findPackage(this.page.model);
        if (!packageItem) {
            return '';
        }
        const md = [];
        const ignoreScopes = this.options.getValue('excludeScopesInPaths');
        const fileExtension = this.options.getValue('fileExtension');
        const entryFileName = `${path.parse(this.options.getValue('entryFileName')).name}${fileExtension}`;
        const packageItemName = packageItem.packageVersion
            ? `${packageItem.name} v${packageItem.packageVersion}`
            : packageItem.name;
        const packagesMeta = this.getPackageMetaData(packageItem.name);
        const entryModule = packagesMeta?.options?.getValue('entryModule');
        const packageEntryFile = ignoreScopes
            ? removeFirstScopedDirectory(`${packageItem.name}${path.sep}${entryFileName}`)
            : `${packageItem.name}${path.sep}${entryFileName}`;
        if (this.page.url === packageEntryFile || Boolean(entryModule)) {
            md.push(bold(packageItemName));
        }
        else {
            md.push(link(bold(packageItemName), this.getRelativeUrl(packageEntryFile)));
        }
        return `${md.join(' • ')}\n\n***\n`;
    };
    function findPackage(model) {
        if (model.kind === ReflectionKind.Module &&
            model.parent?.kind === ReflectionKind.Project) {
            return model;
        }
        if (model.parent) {
            return findPackage(model.parent);
        }
        return null;
    }
    return getHeader();
}

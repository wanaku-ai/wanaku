import { MarkdownTheme } from '../../theme/index.js';
import { UrlMapping } from '../../types/index.js';
import { DeclarationReflection, ProjectReflection, Reflection } from 'typedoc';
/**
 * Map the models of the given project to the desired output files.
 * Based on TypeDoc DefaultTheme.getUrls()
 *
 * @param project  The project whose urls should be generated.
 */
export declare class UrlBuilder {
    theme: MarkdownTheme;
    project: ProjectReflection;
    private options;
    private packagesMeta;
    private fileExtension;
    private ignoreScopes;
    private entryFileName;
    private isPackages;
    private flattenOutputFiles;
    private urls;
    private anchors;
    constructor(theme: MarkdownTheme, project: ProjectReflection);
    getUrls(): UrlMapping<Reflection>[];
    private buildEntryUrls;
    private buildUrlsFromProject;
    private buildUrlsFromPackage;
    private buildUrlsForDocument;
    private buildUrlsFromGroup;
    private traverseChildren;
    private getUrl;
    getFlattenedUrl(reflection: DeclarationReflection): string;
    private getAlias;
    private getUrlPath;
    private applyAnchorUrl;
    private getAnchorId;
    private getAnchorName;
    private moduleHasSubfolders;
    private getIndexFileName;
}

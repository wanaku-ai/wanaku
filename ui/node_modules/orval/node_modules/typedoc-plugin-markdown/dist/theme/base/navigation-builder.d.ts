import { MarkdownTheme } from '../../theme/index.js';
import { NavigationItem } from '../../types/index.js';
import { ProjectReflection } from 'typedoc';
export declare class NavigationBuilder {
    theme: MarkdownTheme;
    project: ProjectReflection;
    private options;
    private packagesMeta;
    private navigationOptions;
    private navigation;
    private isPackages;
    constructor(theme: MarkdownTheme, project: ProjectReflection);
    getNavigation(): NavigationItem[];
    private getNavigationOptions;
    private removeEmptyChildren;
    private buildNavigationFromPackage;
    private buildNavigationFromProject;
    private getCategoryGroupChildren;
    private getGroupChildren;
    private getReflectionGroups;
    private processChildren;
}

import { MarkdownPageEvent } from '../../../events/index.js';
import { MarkdownThemeContext } from '../../../theme/index.js';
import { ProjectReflection } from 'typedoc';
/**
 * Template that maps to the root project reflection. This will be the index page / documentation root page.
 */
export declare function project(this: MarkdownThemeContext, page: MarkdownPageEvent<ProjectReflection>): string;

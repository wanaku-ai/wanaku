import { MarkdownPageEvent } from '../../../events/index.js';
import { MarkdownThemeContext } from '../../../theme/index.js';
import { DocumentReflection } from 'typedoc';
/**
 * Template that maps to a project document.
 */
export declare function document(this: MarkdownThemeContext, page: MarkdownPageEvent<DocumentReflection>): string;

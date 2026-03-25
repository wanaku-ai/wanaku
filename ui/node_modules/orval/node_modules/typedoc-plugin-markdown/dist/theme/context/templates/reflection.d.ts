import { MarkdownPageEvent } from '../../../events/index.js';
import { MarkdownThemeContext } from '../../../theme/index.js';
import { DeclarationReflection } from 'typedoc';
/**
 * Template that maps to individual reflection models.
 */
export declare function reflection(this: MarkdownThemeContext, page: MarkdownPageEvent<DeclarationReflection>): string;

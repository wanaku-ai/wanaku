import { backTicks, bold, heading } from '../../../libs/markdown/index.js';
import { camelToTitleCase, sanitizeComments, } from '../../../libs/utils/index.js';
export function comment(model, options = {}) {
    const opts = {
        headingLevel: undefined,
        showSummary: true,
        showTags: true,
        showReturns: false,
        isTableColumn: false,
        ...options,
    };
    const md = [];
    //Add flags and summary
    if (opts.showSummary) {
        // Add flags
        const flagsNotRendered = [
            '@showCategories',
            '@showGroups',
            '@hideCategories',
            '@hideGroups',
        ];
        const flags = [];
        for (const tag of model.modifierTags) {
            if (!flagsNotRendered.includes(tag)) {
                flags.push(bold(backTicks(camelToTitleCase(tag.substring(1)))));
            }
        }
        md.push(flags.join(' '));
        // Add summary
        if (model.summary?.length > 0) {
            md.push(this.helpers.getCommentParts(model.summary));
        }
    }
    const blockTagsPreserveOrder = this.options.getValue('blockTagsPreserveOrder');
    const showTags = opts.showTags || (opts.showSummary && blockTagsPreserveOrder.length > 0);
    // Add Tags
    if (showTags && model.blockTags?.length > 0) {
        const blockTags = model.blockTags.reduce((previous, current) => {
            if (current.tag === '@example') {
                const prevExampleTag = previous.find((tag) => ['@example', '@examples'].includes(tag.tag));
                if (prevExampleTag) {
                    return previous.map((prevTag) => {
                        if (prevTag === prevExampleTag) {
                            current.content.unshift({ kind: 'text', text: '\n\n' });
                            return {
                                ...prevTag,
                                tag: '@examples',
                                content: [...prevTag.content, ...current.content],
                            };
                        }
                        return prevTag;
                    });
                }
            }
            return [...previous, current];
        }, []);
        const filteredBlockTags = [
            ...this.options.getValue('notRenderedTags'),
            ...(opts.showReturns ? [] : ['@returns']),
        ];
        const tags = blockTags
            .filter((tag) => !filteredBlockTags.includes(tag.tag))
            .filter((tag) => {
            if (!opts.isTableColumn &&
                !(opts.showSummary && opts.showTags) &&
                blockTagsPreserveOrder.length) {
                return opts.showSummary
                    ? blockTagsPreserveOrder.includes(tag.tag)
                    : !blockTagsPreserveOrder.includes(tag.tag);
            }
            return true;
        })
            .filter((tag) => !opts.isTableColumn ||
            (opts.isTableColumn && tag.tag !== '@defaultValue'))
            .map((tag) => {
            const tagText = this.internationalization.translateTagName(tag.tag);
            const tagMd = [
                opts.headingLevel
                    ? heading(opts.headingLevel, tagText) + '\n'
                    : bold(tagText) + '\n',
            ];
            tagMd.push(this.helpers.getCommentParts(tag.content));
            return tagMd.join('\n');
        });
        md.push(tags.join('\n\n'));
    }
    const output = md.join('\n\n');
    const parsedOutput = this.options.getValue('sanitizeComments')
        ? sanitizeComments(output)
        : output;
    return parsedOutput;
}

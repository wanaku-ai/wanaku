/**
 * An event emitted before and after the markdown of a page is rendered.
 *
 * @event
 */
export class MarkdownPageEvent extends Event {
    /**
     * The {@linkcode typedoc!ProjectReflection ProjectReflection} instance the renderer is currently processing.
     */
    project;
    /**
     * The model that that is being rendered on this page.
     * Either a {@linkcode typedoc!DeclarationReflection DeclarationReflection} or {@linkcode typedoc!ProjectReflection ProjectReflection}.
     */
    model;
    /**
     * The group title of the group reflection belongs to.
     */
    group;
    /**
     * The final markdown `string` content of the page.
     *
     * Should be rendered by layout templates and can be modified by plugins.
     */
    contents;
    /**
     * The url `string` of the page.
     */
    url;
    /**
     * The complete `string` filename where the file will be written..
     */
    filename;
    /**
     * The frontmatter of this page represented as a key value object. This property can be utilised by other plugins.
     */
    frontmatter;
    // required for typing purposes but not used
    /** @hidden */
    pageHeadings;
    /** @hidden */
    pageSections;
    /** @hidden */
    startNewSection;
    /**
     * Triggered before a document will be rendered.
     * @event
     */
    static BEGIN = 'beginPage';
    /**
     * Triggered after a document has been rendered, just before it is written to disc.
     * @event
     */
    static END = 'endPage';
    /**
     * @ignore
     */
    constructor(name, model) {
        super(name);
        this.model = model;
    }
}

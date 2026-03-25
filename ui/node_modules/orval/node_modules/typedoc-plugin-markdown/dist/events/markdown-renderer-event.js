import { MarkdownPageEvent } from '../events/index.js';
import * as path from 'path';
/**
 * An event emitted at the beginning and end of the rendering process.
 *
 * @event
 */
export class MarkdownRendererEvent extends Event {
    /**
     * The project the renderer is currently processing.
     */
    project;
    /**
     * The path of the directory the documentation should be written to.
     */
    outputDirectory;
    /**
     * A list of all pages that should be generated.
     */
    urls;
    /**
     * The navigation structure of the project that can be utilised by plugins.
     */
    navigation;
    /**
     * Triggered before the renderer starts rendering a project.
     * @event
     */
    static BEGIN = 'beginRender';
    /**
     * Triggered after the renderer has written all documents.
     * @event
     */
    static END = 'endRender';
    /**
     * @ignore
     */
    constructor(name, outputDirectory, project) {
        super(name);
        this.outputDirectory = outputDirectory;
        this.project = project;
    }
    /**
     * @ignore
     */
    createPageEvent(mapping) {
        const event = new MarkdownPageEvent(MarkdownPageEvent.BEGIN, mapping.model);
        event.group = mapping.group;
        event.project = this.project;
        event.url = mapping.url;
        event.filename = path.join(this.outputDirectory, mapping.url);
        return [mapping.template, event];
    }
}

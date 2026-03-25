import { RendererComponent } from "./components.js";
/**
 * Base class of all themes.
 *
 * The theme class controls which files will be created through the {@link Theme.getUrls}
 * function. It returns an array of {@link UrlMapping} instances defining the target files, models
 * and templates to use. Additionally themes can subscribe to the events emitted by
 * {@link Renderer} to control and manipulate the output process.
 */
export class Theme extends RendererComponent {
    sluggers = new Map();
    setSlugger(reflection, slugger) {
        this.sluggers.set(reflection, slugger);
    }
    getSlugger(reflection) {
        if (this.sluggers.has(reflection)) {
            return this.sluggers.get(reflection);
        }
        // A slugger should always be defined at least for the project
        return this.getSlugger(reflection.parent);
    }
}

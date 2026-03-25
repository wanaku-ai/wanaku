var __esDecorate = (this && this.__esDecorate) || function (ctor, descriptorIn, decorators, contextIn, initializers, extraInitializers) {
    function accept(f) { if (f !== void 0 && typeof f !== "function") throw new TypeError("Function expected"); return f; }
    var kind = contextIn.kind, key = kind === "getter" ? "get" : kind === "setter" ? "set" : "value";
    var target = !descriptorIn && ctor ? contextIn["static"] ? ctor : ctor.prototype : null;
    var descriptor = descriptorIn || (target ? Object.getOwnPropertyDescriptor(target, contextIn.name) : {});
    var _, done = false;
    for (var i = decorators.length - 1; i >= 0; i--) {
        var context = {};
        for (var p in contextIn) context[p] = p === "access" ? {} : contextIn[p];
        for (var p in contextIn.access) context.access[p] = contextIn.access[p];
        context.addInitializer = function (f) { if (done) throw new TypeError("Cannot add initializers after decoration has completed"); extraInitializers.push(accept(f || null)); };
        var result = (0, decorators[i])(kind === "accessor" ? { get: descriptor.get, set: descriptor.set } : descriptor[key], context);
        if (kind === "accessor") {
            if (result === void 0) continue;
            if (result === null || typeof result !== "object") throw new TypeError("Object expected");
            if (_ = accept(result.get)) descriptor.get = _;
            if (_ = accept(result.set)) descriptor.set = _;
            if (_ = accept(result.init)) initializers.unshift(_);
        }
        else if (_ = accept(result)) {
            if (kind === "field") initializers.unshift(_);
            else descriptor[key] = _;
        }
    }
    if (target) Object.defineProperty(target, contextIn.name, descriptor);
    done = true;
};
var __runInitializers = (this && this.__runInitializers) || function (thisArg, initializers, value) {
    var useValue = arguments.length > 2;
    for (var i = 0; i < initializers.length; i++) {
        value = useValue ? initializers[i].call(thisArg, value) : initializers[i].call(thisArg);
    }
    return useValue ? value : void 0;
};
/**
 * Holds all logic used render and output the final documentation.
 *
 * The {@link Renderer} class is the central controller within this namespace. When invoked it creates
 * an instance of {@link Theme} which defines the layout of the documentation and fires a
 * series of {@link RendererEvent} events. Instances of {@link BasePlugin} can listen to these events and
 * alter the generated output.
 */
import * as fs from "fs";
import * as path from "path";
import { RendererEvent, PageEvent, IndexEvent, } from "./events.js";
import { writeFileSync } from "../utils/fs.js";
import { DefaultTheme } from "./themes/default/DefaultTheme.js";
import { Option, EventHooks, AbstractComponent } from "../utils/index.js";
import { loadHighlighter } from "../utils/highlighter.js";
import { Reflection } from "../models/index.js";
import { setRenderSettings } from "../utils/jsx.js";
import { AssetsPlugin, HierarchyPlugin, IconsPlugin, JavascriptIndexPlugin, MarkedPlugin, NavigationPlugin, SitemapPlugin, } from "./plugins/index.js";
/**
 * The renderer processes a {@link ProjectReflection} using a {@link Theme} instance and writes
 * the emitted html documents to a output directory. You can specify which theme should be used
 * using the `--theme <name>` command line argument.
 *
 * {@link Renderer} is a subclass of {@link EventDispatcher} and triggers a series of events while
 * a project is being processed. You can listen to these events to control the flow or manipulate
 * the output.
 *
 *  * {@link Renderer.EVENT_BEGIN}<br>
 *    Triggered before the renderer starts rendering a project. The listener receives
 *    an instance of {@link RendererEvent}.
 *
 *    * {@link Renderer.EVENT_BEGIN_PAGE}<br>
 *      Triggered before a document will be rendered. The listener receives an instance of
 *      {@link PageEvent}.
 *
 *    * {@link Renderer.EVENT_END_PAGE}<br>
 *      Triggered after a document has been rendered, just before it is written to disc. The
 *      listener receives an instance of {@link PageEvent}.
 *
 *  * {@link Renderer.EVENT_END}<br>
 *    Triggered after the renderer has written all documents. The listener receives
 *    an instance of {@link RendererEvent}.
 *
 * * {@link Renderer.EVENT_PREPARE_INDEX}<br>
 *    Triggered when the JavascriptIndexPlugin is preparing the search index. Listeners receive
 *    an instance of {@link IndexEvent}.
 *
 * @summary Writes HTML output from TypeDoc's models
 * @group Common
 */
let Renderer = (() => {
    let _classSuper = AbstractComponent;
    let _themeName_decorators;
    let _themeName_initializers = [];
    let _themeName_extraInitializers = [];
    let _cleanOutputDir_decorators;
    let _cleanOutputDir_initializers = [];
    let _cleanOutputDir_extraInitializers = [];
    let _cname_decorators;
    let _cname_initializers = [];
    let _cname_extraInitializers = [];
    let _githubPages_decorators;
    let _githubPages_initializers = [];
    let _githubPages_extraInitializers = [];
    let _cacheBust_decorators;
    let _cacheBust_initializers = [];
    let _cacheBust_extraInitializers = [];
    let _lightTheme_decorators;
    let _lightTheme_initializers = [];
    let _lightTheme_extraInitializers = [];
    let _darkTheme_decorators;
    let _darkTheme_initializers = [];
    let _darkTheme_extraInitializers = [];
    let _highlightLanguages_decorators;
    let _highlightLanguages_initializers = [];
    let _highlightLanguages_extraInitializers = [];
    let _ignoredHighlightLanguages_decorators;
    let _ignoredHighlightLanguages_initializers = [];
    let _ignoredHighlightLanguages_extraInitializers = [];
    let _pretty_decorators;
    let _pretty_initializers = [];
    let _pretty_extraInitializers = [];
    return class Renderer extends _classSuper {
        static {
            const _metadata = typeof Symbol === "function" && Symbol.metadata ? Object.create(_classSuper[Symbol.metadata] ?? null) : void 0;
            _themeName_decorators = [Option("theme")];
            _cleanOutputDir_decorators = [Option("cleanOutputDir")];
            _cname_decorators = [Option("cname")];
            _githubPages_decorators = [Option("githubPages")];
            _cacheBust_decorators = [Option("cacheBust")];
            _lightTheme_decorators = [Option("lightHighlightTheme")];
            _darkTheme_decorators = [Option("darkHighlightTheme")];
            _highlightLanguages_decorators = [Option("highlightLanguages")];
            _ignoredHighlightLanguages_decorators = [Option("ignoredHighlightLanguages")];
            _pretty_decorators = [Option("pretty")];
            __esDecorate(this, null, _themeName_decorators, { kind: "accessor", name: "themeName", static: false, private: false, access: { has: obj => "themeName" in obj, get: obj => obj.themeName, set: (obj, value) => { obj.themeName = value; } }, metadata: _metadata }, _themeName_initializers, _themeName_extraInitializers);
            __esDecorate(this, null, _cleanOutputDir_decorators, { kind: "accessor", name: "cleanOutputDir", static: false, private: false, access: { has: obj => "cleanOutputDir" in obj, get: obj => obj.cleanOutputDir, set: (obj, value) => { obj.cleanOutputDir = value; } }, metadata: _metadata }, _cleanOutputDir_initializers, _cleanOutputDir_extraInitializers);
            __esDecorate(this, null, _cname_decorators, { kind: "accessor", name: "cname", static: false, private: false, access: { has: obj => "cname" in obj, get: obj => obj.cname, set: (obj, value) => { obj.cname = value; } }, metadata: _metadata }, _cname_initializers, _cname_extraInitializers);
            __esDecorate(this, null, _githubPages_decorators, { kind: "accessor", name: "githubPages", static: false, private: false, access: { has: obj => "githubPages" in obj, get: obj => obj.githubPages, set: (obj, value) => { obj.githubPages = value; } }, metadata: _metadata }, _githubPages_initializers, _githubPages_extraInitializers);
            __esDecorate(this, null, _cacheBust_decorators, { kind: "accessor", name: "cacheBust", static: false, private: false, access: { has: obj => "cacheBust" in obj, get: obj => obj.cacheBust, set: (obj, value) => { obj.cacheBust = value; } }, metadata: _metadata }, _cacheBust_initializers, _cacheBust_extraInitializers);
            __esDecorate(this, null, _lightTheme_decorators, { kind: "accessor", name: "lightTheme", static: false, private: false, access: { has: obj => "lightTheme" in obj, get: obj => obj.lightTheme, set: (obj, value) => { obj.lightTheme = value; } }, metadata: _metadata }, _lightTheme_initializers, _lightTheme_extraInitializers);
            __esDecorate(this, null, _darkTheme_decorators, { kind: "accessor", name: "darkTheme", static: false, private: false, access: { has: obj => "darkTheme" in obj, get: obj => obj.darkTheme, set: (obj, value) => { obj.darkTheme = value; } }, metadata: _metadata }, _darkTheme_initializers, _darkTheme_extraInitializers);
            __esDecorate(this, null, _highlightLanguages_decorators, { kind: "accessor", name: "highlightLanguages", static: false, private: false, access: { has: obj => "highlightLanguages" in obj, get: obj => obj.highlightLanguages, set: (obj, value) => { obj.highlightLanguages = value; } }, metadata: _metadata }, _highlightLanguages_initializers, _highlightLanguages_extraInitializers);
            __esDecorate(this, null, _ignoredHighlightLanguages_decorators, { kind: "accessor", name: "ignoredHighlightLanguages", static: false, private: false, access: { has: obj => "ignoredHighlightLanguages" in obj, get: obj => obj.ignoredHighlightLanguages, set: (obj, value) => { obj.ignoredHighlightLanguages = value; } }, metadata: _metadata }, _ignoredHighlightLanguages_initializers, _ignoredHighlightLanguages_extraInitializers);
            __esDecorate(this, null, _pretty_decorators, { kind: "accessor", name: "pretty", static: false, private: false, access: { has: obj => "pretty" in obj, get: obj => obj.pretty, set: (obj, value) => { obj.pretty = value; } }, metadata: _metadata }, _pretty_initializers, _pretty_extraInitializers);
            if (_metadata) Object.defineProperty(this, Symbol.metadata, { enumerable: true, configurable: true, writable: true, value: _metadata });
        }
        themes = new Map([
            ["default", DefaultTheme],
        ]);
        /** @event */
        static EVENT_BEGIN_PAGE = PageEvent.BEGIN;
        /** @event */
        static EVENT_END_PAGE = PageEvent.END;
        /** @event */
        static EVENT_BEGIN = RendererEvent.BEGIN;
        /** @event */
        static EVENT_END = RendererEvent.END;
        /** @event */
        static EVENT_PREPARE_INDEX = IndexEvent.PREPARE_INDEX;
        /**
         * A list of async jobs which must be completed *before* rendering output.
         * They will be called after {@link RendererEvent.BEGIN} has fired, but before any files have been written.
         *
         * This may be used by plugins to register work that must be done to prepare output files. For example: asynchronously
         * transform markdown to HTML.
         *
         * Note: This array is cleared after calling the contained functions on each {@link Renderer.render} call.
         */
        preRenderAsyncJobs = [];
        /**
         * A list of async jobs which must be completed after rendering output files but before generation is considered successful.
         * These functions will be called after all documents have been written to the filesystem.
         *
         * This may be used by plugins to register work that must be done to finalize output files. For example: asynchronously
         * generating an image referenced in a render hook.
         *
         * Note: This array is cleared after calling the contained functions on each {@link Renderer.render} call.
         */
        postRenderAsyncJobs = [];
        /**
         * The theme that is used to render the documentation.
         */
        theme;
        /**
         * Hooks which will be called when rendering pages.
         * Note:
         * - Hooks added during output will be discarded at the end of rendering.
         * - Hooks added during a page render will be discarded at the end of that page's render.
         *
         * See {@link RendererHooks} for a description of each available hook, and when it will be called.
         */
        hooks = new EventHooks();
        #themeName_accessor_storage = __runInitializers(this, _themeName_initializers, void 0);
        /** @internal */
        get themeName() { return this.#themeName_accessor_storage; }
        set themeName(value) { this.#themeName_accessor_storage = value; }
        #cleanOutputDir_accessor_storage = (__runInitializers(this, _themeName_extraInitializers), __runInitializers(this, _cleanOutputDir_initializers, void 0));
        get cleanOutputDir() { return this.#cleanOutputDir_accessor_storage; }
        set cleanOutputDir(value) { this.#cleanOutputDir_accessor_storage = value; }
        #cname_accessor_storage = (__runInitializers(this, _cleanOutputDir_extraInitializers), __runInitializers(this, _cname_initializers, void 0));
        get cname() { return this.#cname_accessor_storage; }
        set cname(value) { this.#cname_accessor_storage = value; }
        #githubPages_accessor_storage = (__runInitializers(this, _cname_extraInitializers), __runInitializers(this, _githubPages_initializers, void 0));
        get githubPages() { return this.#githubPages_accessor_storage; }
        set githubPages(value) { this.#githubPages_accessor_storage = value; }
        #cacheBust_accessor_storage = (__runInitializers(this, _githubPages_extraInitializers), __runInitializers(this, _cacheBust_initializers, void 0));
        /** @internal */
        get cacheBust() { return this.#cacheBust_accessor_storage; }
        set cacheBust(value) { this.#cacheBust_accessor_storage = value; }
        #lightTheme_accessor_storage = (__runInitializers(this, _cacheBust_extraInitializers), __runInitializers(this, _lightTheme_initializers, void 0));
        get lightTheme() { return this.#lightTheme_accessor_storage; }
        set lightTheme(value) { this.#lightTheme_accessor_storage = value; }
        #darkTheme_accessor_storage = (__runInitializers(this, _lightTheme_extraInitializers), __runInitializers(this, _darkTheme_initializers, void 0));
        get darkTheme() { return this.#darkTheme_accessor_storage; }
        set darkTheme(value) { this.#darkTheme_accessor_storage = value; }
        #highlightLanguages_accessor_storage = (__runInitializers(this, _darkTheme_extraInitializers), __runInitializers(this, _highlightLanguages_initializers, void 0));
        get highlightLanguages() { return this.#highlightLanguages_accessor_storage; }
        set highlightLanguages(value) { this.#highlightLanguages_accessor_storage = value; }
        #ignoredHighlightLanguages_accessor_storage = (__runInitializers(this, _highlightLanguages_extraInitializers), __runInitializers(this, _ignoredHighlightLanguages_initializers, void 0));
        get ignoredHighlightLanguages() { return this.#ignoredHighlightLanguages_accessor_storage; }
        set ignoredHighlightLanguages(value) { this.#ignoredHighlightLanguages_accessor_storage = value; }
        #pretty_accessor_storage = (__runInitializers(this, _ignoredHighlightLanguages_extraInitializers), __runInitializers(this, _pretty_initializers, void 0));
        get pretty() { return this.#pretty_accessor_storage; }
        set pretty(value) { this.#pretty_accessor_storage = value; }
        renderStartTime = (__runInitializers(this, _pretty_extraInitializers), -1);
        markedPlugin;
        constructor(owner) {
            super(owner);
            this.markedPlugin = new MarkedPlugin(this);
            new AssetsPlugin(this);
            new IconsPlugin(this);
            new HierarchyPlugin(this);
            new JavascriptIndexPlugin(this);
            new NavigationPlugin(this);
            new SitemapPlugin(this);
        }
        /**
         * Define a new theme that can be used to render output.
         * This API will likely be changing at some point, to allow more easily overriding parts of the theme without
         * requiring additional boilerplate.
         * @param name
         * @param theme
         */
        defineTheme(name, theme) {
            if (this.themes.has(name)) {
                throw new Error(`The theme "${name}" has already been defined.`);
            }
            this.themes.set(name, theme);
        }
        /**
         * Render the given project reflection to the specified output directory.
         *
         * @param project  The project that should be rendered.
         * @param outputDirectory  The path of the directory the documentation should be rendered to.
         */
        async render(project, outputDirectory) {
            setRenderSettings({ pretty: this.pretty });
            const momento = this.hooks.saveMomento();
            this.renderStartTime = Date.now();
            if (!this.prepareTheme() ||
                !(await this.prepareOutputDirectory(outputDirectory))) {
                return;
            }
            const output = new RendererEvent(outputDirectory, project);
            output.urls = this.theme.getUrls(project);
            this.trigger(RendererEvent.BEGIN, output);
            await this.runPreRenderJobs(output);
            this.application.logger.verbose(`There are ${output.urls.length} pages to write.`);
            output.urls.forEach((mapping) => {
                this.renderDocument(...output.createPageEvent(mapping));
            });
            await Promise.all(this.postRenderAsyncJobs.map((job) => job(output)));
            this.postRenderAsyncJobs = [];
            this.trigger(RendererEvent.END, output);
            this.theme = void 0;
            this.hooks.restoreMomento(momento);
        }
        async runPreRenderJobs(output) {
            const start = Date.now();
            this.preRenderAsyncJobs.push(this.loadHighlighter.bind(this));
            await Promise.all(this.preRenderAsyncJobs.map((job) => job(output)));
            this.preRenderAsyncJobs = [];
            this.application.logger.verbose(`Pre render async jobs took ${Date.now() - start}ms`);
        }
        async loadHighlighter() {
            await loadHighlighter(this.lightTheme, this.darkTheme, 
            // Checked in option validation
            this.highlightLanguages, this.ignoredHighlightLanguages);
        }
        /**
         * Render a single page.
         *
         * @param page An event describing the current page.
         * @return TRUE if the page has been saved to disc, otherwise FALSE.
         */
        renderDocument(template, page) {
            const momento = this.hooks.saveMomento();
            this.trigger(PageEvent.BEGIN, page);
            if (page.model instanceof Reflection) {
                page.contents = this.theme.render(page, template);
            }
            else {
                throw new Error("Should be unreachable");
            }
            this.trigger(PageEvent.END, page);
            this.hooks.restoreMomento(momento);
            try {
                writeFileSync(page.filename, page.contents);
            }
            catch (error) {
                this.application.logger.error(this.application.i18n.could_not_write_0(page.filename));
            }
        }
        /**
         * Ensure that a theme has been setup.
         *
         * If a the user has set a theme we try to find and load it. If no theme has
         * been specified we load the default theme.
         *
         * @returns TRUE if a theme has been setup, otherwise FALSE.
         */
        prepareTheme() {
            if (!this.theme) {
                const ctor = this.themes.get(this.themeName);
                if (!ctor) {
                    this.application.logger.error(this.application.i18n.theme_0_is_not_defined_available_are_1(this.themeName, [...this.themes.keys()].join(", ")));
                    return false;
                }
                else {
                    this.theme = new ctor(this);
                }
            }
            return true;
        }
        /**
         * Prepare the output directory. If the directory does not exist, it will be
         * created. If the directory exists, it will be emptied.
         *
         * @param directory  The path to the directory that should be prepared.
         * @returns TRUE if the directory could be prepared, otherwise FALSE.
         */
        async prepareOutputDirectory(directory) {
            if (this.cleanOutputDir) {
                try {
                    await fs.promises.rm(directory, {
                        recursive: true,
                        force: true,
                    });
                }
                catch (error) {
                    this.application.logger.warn(this.application.i18n.could_not_empty_output_directory_0(directory));
                    return false;
                }
            }
            try {
                fs.mkdirSync(directory, { recursive: true });
            }
            catch (error) {
                this.application.logger.error(this.application.i18n.could_not_create_output_directory_0(directory));
                return false;
            }
            if (this.githubPages) {
                try {
                    const text = "TypeDoc added this file to prevent GitHub Pages from " +
                        "using Jekyll. You can turn off this behavior by setting " +
                        "the `githubPages` option to false.";
                    fs.writeFileSync(path.join(directory, ".nojekyll"), text);
                }
                catch (error) {
                    this.application.logger.warn(this.application.i18n.could_not_write_0(path.join(directory, ".nojekyll")));
                    return false;
                }
            }
            if (this.cname) {
                fs.writeFileSync(path.join(directory, "CNAME"), this.cname);
            }
            return true;
        }
    };
})();
export { Renderer };

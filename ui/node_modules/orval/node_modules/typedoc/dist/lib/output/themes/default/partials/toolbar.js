import { JSX } from "../../../../utils/index.js";
import { getDisplayName } from "../../lib.js";
export const toolbar = (context, props) => (JSX.createElement("header", { class: "tsd-page-toolbar" },
    JSX.createElement("div", { class: "tsd-toolbar-contents container" },
        JSX.createElement("div", { class: "table-cell", id: "tsd-search" },
            JSX.createElement("div", { class: "field" },
                JSX.createElement("label", { for: "tsd-search-field", class: "tsd-widget tsd-toolbar-icon search no-caption" }, context.icons.search()),
                JSX.createElement("input", { type: "text", id: "tsd-search-field", "aria-label": context.i18n.theme_search() })),
            JSX.createElement("div", { class: "field" },
                JSX.createElement("div", { id: "tsd-toolbar-links" }, Object.entries(context.options.getValue("navigationLinks")).map(([label, url]) => (JSX.createElement("a", { href: url }, label))))),
            JSX.createElement("ul", { class: "results" },
                JSX.createElement("li", { class: "state loading" }, context.i18n.theme_preparing_search_index()),
                JSX.createElement("li", { class: "state failure" }, context.i18n.theme_search_index_not_available())),
            JSX.createElement("a", { href: context.options.getValue("titleLink") || context.relativeURL("index.html"), class: "title" }, getDisplayName(props.project))),
        JSX.createElement("div", { class: "table-cell", id: "tsd-widgets" },
            JSX.createElement("a", { href: "#", class: "tsd-widget tsd-toolbar-icon menu no-caption", "data-toggle": "menu", "aria-label": context.i18n.theme_menu() }, context.icons.menu())))));

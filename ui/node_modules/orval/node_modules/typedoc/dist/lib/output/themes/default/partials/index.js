import { classNames, renderName } from "../../lib.js";
import { JSX, Raw } from "../../../../utils/index.js";
function renderCategory({ urlTo, icons, getReflectionClasses, markdown }, item, prependName = "") {
    return (JSX.createElement("section", { class: "tsd-index-section" },
        JSX.createElement("h3", { class: "tsd-index-heading" }, prependName ? `${prependName} - ${item.title}` : item.title),
        item.description && (JSX.createElement("div", { class: "tsd-comment tsd-typography" },
            JSX.createElement(Raw, { html: markdown(item.description) }))),
        JSX.createElement("div", { class: "tsd-index-list" }, item.children.map((item) => (JSX.createElement(JSX.Fragment, null,
            JSX.createElement("a", { href: urlTo(item), class: classNames({ "tsd-index-link": true, deprecated: item.isDeprecated() }, getReflectionClasses(item)) },
                icons[item.kind](),
                JSX.createElement("span", null, renderName(item))),
            "\n"))))));
}
export function index(context, props) {
    let content = [];
    if (props.categories?.length) {
        content = props.categories.map((item) => renderCategory(context, item));
    }
    else if (props.groups?.length) {
        content = props.groups.flatMap((item) => item.categories
            ? item.categories.map((item2) => renderCategory(context, item2, item.title))
            : renderCategory(context, item));
    }
    // Accordion is only needed if any children don't have their own document.
    if ([...(props.groups ?? []), ...(props.categories ?? [])].some((category) => !category.allChildrenHaveOwnDocument())) {
        content = (JSX.createElement("details", { class: "tsd-index-content tsd-accordion", open: true },
            JSX.createElement("summary", { class: "tsd-accordion-summary tsd-index-summary" },
                JSX.createElement("h5", { class: "tsd-index-heading uppercase", role: "button", "aria-expanded": "false", tabIndex: 0 },
                    context.icons.chevronSmall(),
                    " ",
                    context.i18n.theme_index())),
            JSX.createElement("div", { class: "tsd-accordion-details" }, content)));
    }
    else {
        content = (JSX.createElement(JSX.Fragment, null,
            JSX.createElement("h3", { class: "tsd-index-heading uppercase" }, context.i18n.theme_index()),
            content));
    }
    return (JSX.createElement(JSX.Fragment, null,
        JSX.createElement("section", { class: "tsd-panel-group tsd-index-group" },
            JSX.createElement("section", { class: "tsd-panel tsd-index-panel" }, content))));
}

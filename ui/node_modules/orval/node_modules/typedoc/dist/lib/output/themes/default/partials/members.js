import { JSX } from "../../../../utils/index.js";
import {} from "../../../../models/index.js";
import { getMemberSections } from "../../lib.js";
export function members(context, props) {
    const sections = getMemberSections(props, (child) => !child.hasOwnDocument);
    return (JSX.createElement(JSX.Fragment, null, sections.map(({ title, children }) => {
        context.page.startNewSection(title);
        return (JSX.createElement("details", { class: "tsd-panel-group tsd-member-group tsd-accordion", open: true },
            JSX.createElement("summary", { class: "tsd-accordion-summary", "data-key": "section-" + title },
                JSX.createElement("h2", null,
                    context.icons.chevronDown(),
                    " ",
                    title)),
            JSX.createElement("section", null, children.map((item) => context.member(item)))));
    })));
}

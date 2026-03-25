import { JSX } from "../../../../utils/index.js";
export function anchorIcon(context, anchor) {
    if (!anchor)
        return JSX.createElement(JSX.Fragment, null);
    return (JSX.createElement("a", { href: `#${anchor}`, "aria-label": context.i18n.theme_permalink(), class: "tsd-anchor-icon" }, context.icons.anchor()));
}

import { JSX } from "../../../../utils/index.js";
export const breadcrumb = (context, props) => props.parent ? (JSX.createElement(JSX.Fragment, null,
    context.breadcrumb(props.parent),
    JSX.createElement("li", null, props.url ? JSX.createElement("a", { href: context.urlTo(props) }, props.name) : JSX.createElement("span", null, props.name)))) : props.url ? (JSX.createElement("li", null,
    JSX.createElement("a", { href: context.urlTo(props) }, props.name))) : undefined;

import { ReferenceReflection, ReflectionKind, } from "../../../../models/index.js";
import { JSX, Raw } from "../../../../utils/index.js";
import { classNames, getDisplayName, getMemberSections, getUniquePath, join } from "../../lib.js";
import { anchorIcon } from "./anchor-icon.js";
export function moduleReflection(context, mod) {
    const sections = getMemberSections(mod);
    return (JSX.createElement(JSX.Fragment, null,
        mod.hasComment() && (JSX.createElement("section", { class: "tsd-panel tsd-comment" },
            context.commentSummary(mod),
            context.commentTags(mod))),
        mod.isDeclaration() && mod.kind === ReflectionKind.Module && mod.readme?.length && (JSX.createElement("section", { class: "tsd-panel tsd-typography" },
            JSX.createElement(Raw, { html: context.markdown(mod.readme) }))),
        sections.map(({ title, children, description }) => {
            context.page.startNewSection(title);
            return (JSX.createElement("details", { class: "tsd-panel-group tsd-member-group tsd-accordion", open: true },
                JSX.createElement("summary", { class: "tsd-accordion-summary", "data-key": "section-" + title },
                    JSX.createElement("h2", null,
                        context.icons.chevronDown(),
                        " ",
                        title)),
                description && (JSX.createElement("div", { class: "tsd-comment tsd-typography" },
                    JSX.createElement(Raw, { html: context.markdown(description) }))),
                JSX.createElement("dl", { class: "tsd-member-summaries" }, children.map((item) => context.moduleMemberSummary(item)))));
        })));
}
export function moduleMemberSummary(context, member) {
    const id = context.slugger.slug(member.name);
    context.page.pageHeadings.push({
        link: `#${id}`,
        text: getDisplayName(member),
        kind: member instanceof ReferenceReflection ? member.getTargetReflectionDeep().kind : member.kind,
        classes: context.getReflectionClasses(member),
    });
    let name;
    if (member instanceof ReferenceReflection) {
        const target = member.getTargetReflectionDeep();
        name = (JSX.createElement("span", { class: "tsd-member-summary-name" },
            context.icons[target.kind](),
            JSX.createElement("span", { class: classNames({ deprecated: member.isDeprecated() }) }, member.name),
            JSX.createElement("span", null,
                "\u00A0",
                "\u2192",
                "\u00A0"),
            uniqueName(context, target),
            anchorIcon(context, id)));
    }
    else {
        name = (JSX.createElement("span", { class: "tsd-member-summary-name" },
            context.icons[member.kind](),
            JSX.createElement("a", { class: classNames({ deprecated: member.isDeprecated() }), href: context.urlTo(member) }, member.name),
            anchorIcon(context, id)));
    }
    return (JSX.createElement(JSX.Fragment, null,
        JSX.createElement("dt", { class: classNames({ "tsd-member-summary": true }, context.getReflectionClasses(member)) },
            JSX.createElement("a", { id: id, class: "tsd-anchor" }),
            name),
        JSX.createElement("dd", { class: classNames({ "tsd-member-summary": true }, context.getReflectionClasses(member)) }, context.commentShortSummary(member))));
}
// Note: This version of uniqueName does NOT include colors... they looked weird to me
// when looking at a module page.
function uniqueName(context, reflection) {
    const name = join(".", getUniquePath(reflection), (item) => (JSX.createElement("a", { href: context.urlTo(item), class: classNames({ deprecated: item.isDeprecated() }) }, item.name)));
    return JSX.createElement(JSX.Fragment, null, name);
}

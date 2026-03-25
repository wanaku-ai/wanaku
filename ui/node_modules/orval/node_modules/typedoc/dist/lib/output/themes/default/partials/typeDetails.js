import { ReflectionKind, } from "../../../../models/index.js";
import { JSX } from "../../../../utils/index.js";
import { classNames, getKindClass } from "../../lib.js";
const isUsefulVisitor = {
    array(type) {
        return renderingTypeDetailsIsUseful(type.elementType);
    },
    intersection(type) {
        return type.types.some(renderingTypeDetailsIsUseful);
    },
    union(type) {
        return !!type.elementSummaries || type.types.some(renderingTypeDetailsIsUseful);
    },
    reflection(type) {
        return renderingChildIsUseful(type.declaration);
    },
    reference(type) {
        return shouldExpandReference(type);
    },
};
function renderingTypeDetailsIsUseful(type) {
    return type.visit(isUsefulVisitor) ?? false;
}
export function typeDeclaration(context, type) {
    if (renderingTypeDetailsIsUseful(type)) {
        return (JSX.createElement("div", { class: "tsd-type-declaration" },
            JSX.createElement("h4", null, context.i18n.theme_type_declaration()),
            context.typeDetails(type, true)));
    }
    return null;
}
const expanded = new Set();
function shouldExpandReference(reference) {
    const target = reference.reflection;
    if (reference.highlightedProperties) {
        return !target || expanded.has(target) === false;
    }
    if (!target?.kindOf(ReflectionKind.TypeAlias | ReflectionKind.Interface))
        return false;
    if (!target.comment?.hasModifier("@expand"))
        return false;
    return expanded.has(target) === false;
}
export function typeDetails(context, type, renderAnchors) {
    return typeDetailsImpl(context, type, renderAnchors);
}
export function typeDetailsImpl(context, type, renderAnchors, highlighted) {
    const result = type.visit({
        array(type) {
            return context.typeDetails(type.elementType, renderAnchors);
        },
        intersection(type) {
            return type.types.map((t) => context.typeDetails(t, renderAnchors));
        },
        union(type) {
            const result = [];
            for (let i = 0; i < type.types.length; ++i) {
                result.push(JSX.createElement("li", null,
                    context.type(type.types[i]),
                    context.displayParts(type.elementSummaries?.[i]),
                    context.typeDetailsIfUseful(type.types[i])));
            }
            return JSX.createElement("ul", null, result);
        },
        reflection(type) {
            const declaration = type.declaration;
            if (highlighted) {
                return highlightedDeclarationDetails(context, declaration, renderAnchors, highlighted);
            }
            return declarationDetails(context, declaration, renderAnchors);
        },
        reference(reference) {
            if (shouldExpandReference(reference)) {
                const target = reference.reflection;
                if (!target?.isDeclaration()) {
                    return highlightedPropertyDetails(context, reference.highlightedProperties);
                }
                // Ensure we don't go into an infinite loop here
                expanded.add(target);
                const details = target.type
                    ? context.typeDetails(target.type, renderAnchors)
                    : declarationDetails(context, target, renderAnchors);
                expanded.delete(target);
                return details;
            }
        },
        // tuple??
    });
    if (!result && highlighted) {
        return highlightedPropertyDetails(context, highlighted);
    }
    return result;
}
export function typeDetailsIfUseful(context, type) {
    if (type && renderingTypeDetailsIsUseful(type)) {
        return context.typeDetails(type, false);
    }
}
function highlightedPropertyDetails(context, highlighted) {
    if (!highlighted?.size)
        return;
    return (JSX.createElement("ul", { class: "tsd-parameters" }, Array.from(highlighted.entries(), ([name, parts]) => {
        return (JSX.createElement("li", { class: "tsd-parameter" },
            JSX.createElement("h5", null,
                JSX.createElement("span", null, name)),
            context.displayParts(parts)));
    })));
}
function highlightedDeclarationDetails(context, declaration, renderAnchors, highlightedProperties) {
    return (JSX.createElement("ul", { class: "tsd-parameters" }, declaration
        .getProperties()
        ?.map((child) => highlightedProperties?.has(child.name) &&
        renderChild(context, child, renderAnchors, highlightedProperties.get(child.name)))));
}
function declarationDetails(context, declaration, renderAnchors) {
    return (JSX.createElement(JSX.Fragment, null,
        context.commentSummary(declaration),
        JSX.createElement("ul", { class: "tsd-parameters" },
            declaration.signatures && (JSX.createElement("li", { class: "tsd-parameter-signature" },
                JSX.createElement("ul", { class: classNames({ "tsd-signatures": true }, context.getReflectionClasses(declaration)) }, declaration.signatures.map((item) => (JSX.createElement(JSX.Fragment, null,
                    JSX.createElement("li", { class: "tsd-signature", id: item.anchor }, context.memberSignatureTitle(item, {
                        hideName: true,
                    })),
                    JSX.createElement("li", { class: "tsd-description" }, context.memberSignatureBody(item, {
                        hideSources: true,
                    })))))))),
            declaration.indexSignatures?.map((index) => renderIndexSignature(context, index)),
            declaration.getProperties()?.map((child) => renderChild(context, child, renderAnchors)))));
}
function renderChild(context, child, renderAnchors, highlight) {
    if (child.signatures) {
        return (JSX.createElement("li", { class: "tsd-parameter" },
            JSX.createElement("h5", null,
                !!child.flags.isRest && JSX.createElement("span", { class: "tsd-signature-symbol" }, "..."),
                JSX.createElement("span", { class: getKindClass(child) }, child.name),
                child.anchor && JSX.createElement("a", { id: child.anchor, class: "tsd-anchor" }),
                JSX.createElement("span", { class: "tsd-signature-symbol" },
                    !!child.flags.isOptional && "?",
                    ":"),
                "function"),
            context.memberSignatures(child)));
    }
    function highlightOrComment(refl) {
        if (highlight) {
            return context.displayParts(highlight);
        }
        return (JSX.createElement(JSX.Fragment, null,
            context.commentSummary(refl),
            context.commentTags(refl)));
    }
    // standard type
    if (child.type) {
        return (JSX.createElement("li", { class: "tsd-parameter" },
            JSX.createElement("h5", null,
                context.reflectionFlags(child),
                !!child.flags.isRest && JSX.createElement("span", { class: "tsd-signature-symbol" }, "..."),
                JSX.createElement("span", { class: getKindClass(child) }, child.name),
                child.anchor && JSX.createElement("a", { id: child.anchor, class: "tsd-anchor" }),
                JSX.createElement("span", { class: "tsd-signature-symbol" },
                    !!child.flags.isOptional && "?",
                    ": "),
                context.type(child.type)),
            highlightOrComment(child),
            child.getProperties().some(renderingChildIsUseful) && (JSX.createElement("ul", { class: "tsd-parameters" }, child.getProperties().map((c) => renderChild(context, c, renderAnchors))))));
    }
    // getter/setter
    return (JSX.createElement(JSX.Fragment, null,
        child.getSignature && (JSX.createElement("li", { class: "tsd-parameter" },
            JSX.createElement("h5", null,
                context.reflectionFlags(child.getSignature),
                JSX.createElement("span", { class: "tsd-signature-keyword" }, "get "),
                JSX.createElement("span", { class: getKindClass(child) }, child.name),
                child.anchor && JSX.createElement("a", { id: child.anchor, class: "tsd-anchor" }),
                JSX.createElement("span", { class: "tsd-signature-symbol" }, "(): "),
                context.type(child.getSignature.type)),
            highlightOrComment(child.getSignature))),
        child.setSignature && (JSX.createElement("li", { class: "tsd-parameter" },
            JSX.createElement("h5", null,
                context.reflectionFlags(child.setSignature),
                JSX.createElement("span", { class: "tsd-signature-keyword" }, "set "),
                JSX.createElement("span", { class: getKindClass(child) }, child.name),
                !child.getSignature && child.anchor && JSX.createElement("a", { id: child.anchor, class: "tsd-anchor" }),
                JSX.createElement("span", { class: "tsd-signature-symbol" }, "("),
                child.setSignature.parameters?.map((item) => (JSX.createElement(JSX.Fragment, null,
                    item.name,
                    JSX.createElement("span", { class: "tsd-signature-symbol" }, ": "),
                    context.type(item.type)))),
                JSX.createElement("span", { class: "tsd-signature-symbol" }, "): "),
                context.type(child.setSignature.type)),
            highlightOrComment(child.setSignature)))));
}
function renderIndexSignature(context, index) {
    return (JSX.createElement("li", { class: "tsd-parameter-index-signature" },
        JSX.createElement("h5", null,
            index.flags.isReadonly && JSX.createElement("span", { class: "tsd-signature-keyword" }, "readonly "),
            JSX.createElement("span", { class: "tsd-signature-symbol" }, "["),
            index.parameters.map((item) => (JSX.createElement(JSX.Fragment, null,
                JSX.createElement("span", { class: getKindClass(item) }, item.name),
                ": ",
                context.type(item.type)))),
            JSX.createElement("span", { class: "tsd-signature-symbol" }, "]: "),
            context.type(index.type)),
        context.commentSummary(index),
        context.commentTags(index),
        context.typeDeclaration(index.type)));
}
function renderingChildIsUseful(refl) {
    // Object types directly under a variable/type alias will always be considered useful.
    // This probably isn't ideal, but it is an easy thing to check when assigning URLs
    // in the default theme, so we'll make the assumption that those properties ought to always
    // be rendered.
    // This should be kept in sync with the DefaultTheme.applyAnchorUrl function.
    if (refl.kindOf(ReflectionKind.TypeLiteral) &&
        refl.parent?.kindOf(ReflectionKind.SomeExport) &&
        refl.parent.type?.type === "reflection") {
        return true;
    }
    if (renderingThisChildIsUseful(refl)) {
        return true;
    }
    return refl.getProperties().some(renderingThisChildIsUseful);
}
function renderingThisChildIsUseful(refl) {
    if (refl.hasComment())
        return true;
    const declaration = refl.type?.type === "reflection" ? refl.type.declaration : refl;
    if (declaration.hasComment())
        return true;
    return declaration.getAllSignatures().some((sig) => {
        return sig.hasComment() || sig.parameters?.some((p) => p.hasComment());
    });
}

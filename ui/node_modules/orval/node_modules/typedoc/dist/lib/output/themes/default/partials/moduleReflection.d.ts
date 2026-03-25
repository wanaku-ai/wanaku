import { type DocumentReflection, type DeclarationReflection, type ProjectReflection } from "../../../../models/index.js";
import { JSX } from "../../../../utils/index.js";
import type { DefaultThemeRenderContext } from "../DefaultThemeRenderContext.js";
export declare function moduleReflection(context: DefaultThemeRenderContext, mod: DeclarationReflection | ProjectReflection): JSX.Element;
export declare function moduleMemberSummary(context: DefaultThemeRenderContext, member: DeclarationReflection | DocumentReflection): JSX.Element;

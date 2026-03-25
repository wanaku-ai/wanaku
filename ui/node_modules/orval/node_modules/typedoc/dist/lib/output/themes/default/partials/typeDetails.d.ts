import { type CommentDisplayPart } from "../../../../models/index.js";
import type { SomeType } from "../../../../models/types.js";
import { JSX } from "../../../../utils/index.js";
import type { DefaultThemeRenderContext } from "../DefaultThemeRenderContext.js";
export declare function typeDeclaration(context: DefaultThemeRenderContext, type: SomeType): JSX.Children;
export declare function typeDetails(context: DefaultThemeRenderContext, type: SomeType, renderAnchors: boolean): JSX.Children;
export declare function typeDetailsImpl(context: DefaultThemeRenderContext, type: SomeType, renderAnchors: boolean, highlighted?: Map<string, CommentDisplayPart[]>): JSX.Children;
export declare function typeDetailsIfUseful(context: DefaultThemeRenderContext, type: SomeType | undefined): JSX.Children;

import { RefObject } from "react";
declare type ObservedSize = {
    width: number | undefined;
    height: number | undefined;
};
declare type ResizeHandler = (size: ObservedSize) => void;
declare function useResizeObserver<T extends HTMLElement>(opts?: {
    onResize?: ResizeHandler;
}): {
    ref: RefObject<T>;
} & ObservedSize;
declare function useResizeObserver<T extends HTMLElement>(opts?: {
    ref: RefObject<T>;
    onResize?: ResizeHandler;
}): {
    ref: RefObject<T>;
} & ObservedSize;
export default useResizeObserver;

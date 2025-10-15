declare module '*.png';
declare module '*.svg';
declare let NODE_ENV: string;
declare let VITE_API_URL: string;
declare let __GIT_HASH: string;
declare let __GIT_DATE: string;
declare let __UI_VERSION: string;

declare module '*?inline' {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const src: any;
  export default src;
}

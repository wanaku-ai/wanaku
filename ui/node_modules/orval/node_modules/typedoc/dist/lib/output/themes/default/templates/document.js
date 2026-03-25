import { JSX, Raw } from "../../../../utils/index.js";
export const documentTemplate = ({ markdown }, props) => (JSX.createElement("div", { class: "tsd-panel tsd-typography" },
    JSX.createElement(Raw, { html: markdown(props.model.content) })));

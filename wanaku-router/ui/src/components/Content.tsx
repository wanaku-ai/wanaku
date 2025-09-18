import { Content } from '@carbon/react';
import { Outlet } from "react-router-dom";

function ContentComponent() {
    return (
        <Content id="main-content">
            <Outlet />
        </Content>
    );
}

export default ContentComponent;
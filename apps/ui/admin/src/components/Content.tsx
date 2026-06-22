import {Content} from '@carbon/react';
import {Outlet, useLocation} from "react-router-dom";
import ErrorBoundary from "./ErrorBoundary";

function ContentComponent() {
    const location = useLocation();
    return (
        <Content id="main-content">
            <ErrorBoundary key={location.pathname}>
                <Outlet />
            </ErrorBoundary>
        </Content>
    );
}

export default ContentComponent;
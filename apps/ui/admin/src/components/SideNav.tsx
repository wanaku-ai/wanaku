import {SideNav, SideNavItems, SideNavMenu, SideNavMenuItem,} from '@carbon/react';
import {Links} from "../router/links.models";
import {Link} from 'react-router-dom';

interface SideNavComponentProps {
    isSideNavExpanded: boolean;
    onClickSideNavExpand: () => void;
}

function SideNavComponent({ isSideNavExpanded, onClickSideNavExpand }:SideNavComponentProps) {
    return (
        <SideNav
            aria-label="Side navigation"
            expanded={isSideNavExpanded}
            isPersistent={false}
            onOverlayClick={onClickSideNavExpand}
        >
            <SideNavItems>
                <SideNavMenuItem
                    element={Link}
                    to="/home"
                    onClick={onClickSideNavExpand}
                >
                    Home
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to="/tools"
                    onClick={onClickSideNavExpand}
                >
                    Tools
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to="/resources"
                    onClick={onClickSideNavExpand}
                >
                    Resources
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to="/prompts"
                    onClick={onClickSideNavExpand}
                >
                    Prompts
                </SideNavMenuItem>
                <SideNavMenu title="Developer">
                    <SideNavMenuItem
                        element={Link}
                        to={Links.LLMChat}
                        onClick={onClickSideNavExpand}
                    >
                        LLMChat
                    </SideNavMenuItem>
                    <SideNavMenuItem
                        element={Link}
                        to={Links.CodeExecution}
                        onClick={onClickSideNavExpand}
                    >
                        Code Execution
                    </SideNavMenuItem>
                    <SideNavMenuItem
                        element={Link}
                        to={Links.ToolCalls}
                        onClick={onClickSideNavExpand}
                    >
                        Tool Call Debugger
                    </SideNavMenuItem>
                </SideNavMenu>
                <SideNavMenuItem
                    element={Link}
                    to="/capabilities"
                    onClick={onClickSideNavExpand}
                >
                    Capabilities
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to="/namespaces"
                    onClick={onClickSideNavExpand}
                >
                    Namespaces
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to={Links.Forwards}
                    onClick={onClickSideNavExpand}
                >
                    Forwards
                </SideNavMenuItem>
                <SideNavMenuItem
                    element={Link}
                    to={Links.DataStores}
                    onClick={onClickSideNavExpand}
                >
                    Data Stores
                </SideNavMenuItem>
            </SideNavItems>
        </SideNav>
    );
}

export default SideNavComponent;
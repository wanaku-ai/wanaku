import {
    Header,
    SkipToContent,
    HeaderMenuButton,
    HeaderName,
    HeaderNavigation,
    HeaderMenuItem,
    HeaderGlobalAction,
    HeaderGlobalBar
} from '@carbon/react';

import { Link } from 'react-router-dom';
import { ExternalLinks, Links } from "../router/links.models";

import {LogoGithub, Logout, Notification, Search} from "@carbon/icons-react";
import wanakuLogo from "../assets/wanaku.svg";


interface HeaderComponentProps {
    onClickSideNavExpand: () => void;
    isSideNavExpanded: boolean;
}


function HeaderComponent({ onClickSideNavExpand, isSideNavExpanded }:HeaderComponentProps) {
    const action = (click: string) => () => {
        console.log(click);
    };
    return (
        <Header aria-label="Platform Name">
            <SkipToContent />
            <HeaderMenuButton
                aria-label={isSideNavExpanded ? "Close menu" : "Open menu"}
                onClick={onClickSideNavExpand}
                isActive={isSideNavExpanded}
                aria-expanded={isSideNavExpanded}
            />
            <HeaderName href={ExternalLinks.Home} target="_blank" prefix="">
                <img
                    src={wanakuLogo}
                    alt="Wanaku"
                    style={{ marginRight: "1em" }}
                />
                Wanaku
            </HeaderName>

            <HeaderNavigation aria-label="Wanaku">
                <HeaderMenuItem as={Link} to={Links.Home}>
                    Home
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Tools}>
                    Tools
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Resources}>
                    Resources
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Prompts}>
                    Prompts
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.LLMChat}>
                    LLMChat
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.CodeExecution}>
                    Code Execution
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Capabilities}>
                    Capabilities
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Namespaces}>
                    Namespaces
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.Forwards}>
                    Forwards
                </HeaderMenuItem>
                <HeaderMenuItem as={Link} to={Links.DataStores}>
                    Data Stores
                </HeaderMenuItem>
            </HeaderNavigation>
            <HeaderGlobalBar>
                <HeaderGlobalAction
                    aria-label="Search"
                    onClick={action("search click")}
                >
                    <Search size={20} />
                </HeaderGlobalAction>
                <HeaderGlobalAction
                    aria-label="Notifications"
                    onClick={action("notification click")}
                >
                    <Notification size={20} />
                </HeaderGlobalAction>
                <HeaderGlobalAction
                    aria-label="GitHub"
                    onClick={() => {
                        window.open(ExternalLinks.GitHub, "_blank");
                    }}
                    tooltipAlignment="end"
                >
                    <LogoGithub size={20} />
                </HeaderGlobalAction>
                <HeaderGlobalAction
                    aria-label="Logout"
                    onClick={() => {
                        window.open(Links.Logout);
                    }}
                >
                    <Logout size={20} />
                </HeaderGlobalAction>
            </HeaderGlobalBar>
        </Header>
    );
}


export default HeaderComponent;
import {
    Header,
    HeaderGlobalAction,
    HeaderGlobalBar,
    HeaderMenu,
    HeaderMenuButton,
    HeaderMenuItem,
    HeaderName,
    HeaderNavigation,
    SkipToContent
} from '@carbon/react';

import {Link} from 'react-router-dom';
import {ExternalLinks, Links} from "../router/links.models";

import {LogoGithub, Logout, Notification, Search} from "@carbon/icons-react";
import wanakuLogo from "../assets/wanaku.svg";
import { useNavigation } from "../contexts/NavigationContext";


interface HeaderComponentProps {
    onClickSideNavExpand: () => void;
    isSideNavExpanded: boolean;
}


function HeaderComponent({ onClickSideNavExpand, isSideNavExpanded }:HeaderComponentProps) {
    const action = (click: string) => () => {
        console.log(click);
    };
    const { items } = useNavigation();

    const ungroupedItems = items.filter(item => !item.section);
    const sections = Array.from(new Set(items.filter(item => item.section).map(item => item.section)));

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
                {ungroupedItems.map(item => (
                    <HeaderMenuItem key={item.id} as={Link} to={item.route}>
                        {item.label}
                    </HeaderMenuItem>
                ))}
                {sections.map(section => {
                    const sectionItems = items.filter(item => item.section === section);
                    return (
                        <HeaderMenu key={section} aria-label={section || ""} menuLinkName={section || ""}>
                            {sectionItems.map(item => (
                                <HeaderMenuItem key={item.id} as={Link} to={item.route}>
                                    {item.label}
                                </HeaderMenuItem>
                            ))}
                        </HeaderMenu>
                    );
                })}
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
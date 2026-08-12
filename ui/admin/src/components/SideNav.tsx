import {SideNav, SideNavItems, SideNavMenu, SideNavMenuItem,} from '@carbon/react';
import {Link} from 'react-router-dom';
import { useNavigation } from "../contexts/NavigationContext";

interface SideNavComponentProps {
    isSideNavExpanded: boolean;
    onClickSideNavExpand: () => void;
}

function SideNavComponent({ isSideNavExpanded, onClickSideNavExpand }:SideNavComponentProps) {
    const { items } = useNavigation();

    const ungroupedItems = items.filter(item => !item.section);
    const sections = Array.from(new Set(items.filter(item => item.section).map(item => item.section)));

    return (
        <SideNav
            aria-label="Side navigation"
            expanded={isSideNavExpanded}
            isPersistent={false}
            onOverlayClick={onClickSideNavExpand}
        >
            <SideNavItems>
                {ungroupedItems.map(item => (
                    <SideNavMenuItem
                        key={item.id}
                        element={Link}
                        to={item.route}
                        onClick={onClickSideNavExpand}
                    >
                        {item.label}
                    </SideNavMenuItem>
                ))}
                {sections.map(section => {
                    const sectionItems = items.filter(item => item.section === section);
                    return (
                        <SideNavMenu key={section} title={section || ""}>
                            {sectionItems.map(item => (
                                <SideNavMenuItem
                                    key={item.id}
                                    element={Link}
                                    to={item.route}
                                    onClick={onClickSideNavExpand}
                                >
                                    {item.label}
                                </SideNavMenuItem>
                            ))}
                        </SideNavMenu>
                    );
                })}
            </SideNavItems>
        </SideNav>
    );
}

export default SideNavComponent;
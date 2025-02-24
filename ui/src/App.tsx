import { Fade, Notification, Search, Switcher } from "@carbon/icons-react";
import {
  Content,
  Header,
  HeaderContainer,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderMenu,
  HeaderMenuButton,
  HeaderMenuItem,
  HeaderName,
  HeaderNavigation,
  HeaderSideNavItems,
  SideNav,
  SideNavItems,
  SideNavLink,
  SideNavMenu,
  SideNavMenuItem,
  SkipToContent,
} from "@carbon/react";
import { Link, Outlet } from "react-router-dom";
import "./App.scss";
import { Links } from "./router/links.models";

function App() {
  const action = (click: string) => () => {
    console.log(click);
  };

  return (
    <HeaderContainer
      render={({ isSideNavExpanded, onClickSideNavExpand }) => (
        <>
          <Header aria-label="Platform Name">
            <SkipToContent />
            <HeaderMenuButton
              aria-label={isSideNavExpanded ? "Close menu" : "Open menu"}
              onClick={onClickSideNavExpand}
              isActive={isSideNavExpanded}
              aria-expanded={isSideNavExpanded}
            />
            <HeaderName href="#" prefix="">
              Wanaku
            </HeaderName>

            <HeaderNavigation aria-label="Wanaku">
              <HeaderMenuItem as={Link} to={Links.Home}>
                Home
              </HeaderMenuItem>
              <HeaderMenuItem as={Link} to={Links.About}>
                About
              </HeaderMenuItem>
              <HeaderMenuItem href="#">Link 3</HeaderMenuItem>
              <HeaderMenu aria-label="Link 4" menuLinkName="Link 4">
                <HeaderMenuItem href="#one">Sub-link 1</HeaderMenuItem>
                <HeaderMenuItem href="#two">Sub-link 2</HeaderMenuItem>
                <HeaderMenuItem href="#three">Sub-link 3</HeaderMenuItem>
              </HeaderMenu>
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
                aria-label="App Switcher"
                onClick={action("app-switcher click")}
                tooltipAlignment="end"
              >
                <Switcher size={20} />
              </HeaderGlobalAction>
            </HeaderGlobalBar>
            <SideNav
              aria-label="Side navigation"
              expanded={isSideNavExpanded}
              onSideNavBlur={onClickSideNavExpand}
              href="#main-content"
            >
              <SideNavItems>
                <HeaderSideNavItems hasDivider={true}>
                  <HeaderMenuItem href="#">Link 1</HeaderMenuItem>
                  <HeaderMenuItem href="#">Link 2</HeaderMenuItem>
                  <HeaderMenuItem href="#">Link 3</HeaderMenuItem>
                  <HeaderMenu aria-label="Link 4" menuLinkName="Link 4">
                    <HeaderMenuItem href="#">Sub-link 1</HeaderMenuItem>
                    <HeaderMenuItem href="#">Sub-link 2</HeaderMenuItem>
                    <HeaderMenuItem href="#">Sub-link 3</HeaderMenuItem>
                  </HeaderMenu>
                </HeaderSideNavItems>
                <SideNavMenu
                  renderIcon={Fade}
                  title="Category title"
                  tabIndex={0}
                >
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 5
                  </SideNavMenuItem>
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 6
                  </SideNavMenuItem>
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 7
                  </SideNavMenuItem>
                </SideNavMenu>
                <SideNavMenu
                  renderIcon={Fade}
                  title="Category title"
                  tabIndex={0}
                >
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 8
                  </SideNavMenuItem>
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 9
                  </SideNavMenuItem>
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 10
                  </SideNavMenuItem>
                </SideNavMenu>
                <SideNavMenu
                  renderIcon={Fade}
                  title="Category title"
                  isActive={true}
                  tabIndex={0}
                >
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 11
                  </SideNavMenuItem>
                  <SideNavMenuItem
                    aria-current="page"
                    href="https://www.carbondesignsystem.com/"
                  >
                    Link 12
                  </SideNavMenuItem>
                  <SideNavMenuItem href="https://www.carbondesignsystem.com/">
                    Link 13
                  </SideNavMenuItem>
                </SideNavMenu>
                <SideNavLink
                  renderIcon={Fade}
                  href="https://www.carbondesignsystem.com/"
                >
                  Link
                </SideNavLink>
                <SideNavLink
                  renderIcon={Fade}
                  href="https://www.carbondesignsystem.com/"
                >
                  Link
                </SideNavLink>
              </SideNavItems>
            </SideNav>
          </Header>

          <Content id="main-content">
            <div className="cds--grid">
              <div className="cds--row">
                <div className="cds--col-lg-13 cds--offset-lg-3">
                  <Outlet />
                </div>
              </div>
            </div>
          </Content>
        </>
      )}
    />
  );
}

export default App;

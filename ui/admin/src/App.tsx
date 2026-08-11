import {HeaderContainer} from "@carbon/react";
import {listNamespaces} from "./hooks/api/use-namespaces";
import "./App.scss";

import Header from "./components/Header";
import SideNav from "./components/SideNav";
import Content from "./components/Content";
import { NotificationProvider } from "./contexts/NotificationContext";
import { NavigationProvider } from "./contexts/NavigationContext";
import { getInitialNavItems } from "./plugins/plugin-state";

function App() {

  listNamespaces(); // pre load namespaces and instatiate singleton during startup

  return (
    <NotificationProvider>
      <NavigationProvider initialItems={getInitialNavItems()}>
        <HeaderContainer
          render={({ isSideNavExpanded, onClickSideNavExpand }) => (
            <>
              <Header isSideNavExpanded={isSideNavExpanded} onClickSideNavExpand={onClickSideNavExpand}/>
              <SideNav isSideNavExpanded={isSideNavExpanded} onClickSideNavExpand={onClickSideNavExpand}/>
              <Content/>
            </>
          )}
        />
      </NavigationProvider>
    </NotificationProvider>
  );
}

export default App;

import * as React from 'react';
import '@patternfly/react-core/dist/styles/base.css';
import { BrowserRouter as Router } from 'react-router-dom';
import { AppLayout } from './AppLayout/AppLayout';
import { AppRoutes } from './routes';
import './app.scss';

const App: React.FunctionComponent = () => (
  <Router>
    <AppLayout>
      <AppRoutes />
    </AppLayout>
  </Router>
);

export default App;

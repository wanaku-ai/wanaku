import {FunctionComponent} from "react";
import {useRouteError, isRouteErrorResponse, Link} from "react-router-dom";
import {InlineNotification, Button} from "@carbon/react";

export const ErrorPage: FunctionComponent = () => {
  const error = useRouteError();

  let message = "An unexpected error occurred.";
  if (isRouteErrorResponse(error)) {
    message = error.status === 404 ? "Page not found." : `${error.status}: ${error.statusText}`;
  } else if (error instanceof Error) {
    message = error.message;
  }

  return (
    <div style={{padding: "2rem"}}>
      <h1>Something went wrong</h1>
      <InlineNotification
        kind="error"
        title="Error"
        subtitle={message}
        hideCloseButton
      />
      <Link to="/">
        <Button kind="primary" style={{marginTop: "1rem"}}>
          Go to Dashboard
        </Button>
      </Link>
    </div>
  );
};

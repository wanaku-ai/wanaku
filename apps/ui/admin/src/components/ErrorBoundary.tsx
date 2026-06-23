import { Component, ErrorInfo, ReactNode } from "react";
import { InlineNotification, Button } from "@carbon/react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Uncaught error in page component:", error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: "2rem" }}>
          <InlineNotification
            kind="error"
            title="Something went wrong"
            subtitle={this.state.error?.message || "An unexpected error occurred"}
            hideCloseButton
          />
          <Button
            kind="primary"
            style={{ marginTop: "1rem" }}
            onClick={() => {
              window.location.hash = "#/";
              this.setState({ hasError: false, error: null });
            }}
          >
            Go to Dashboard
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;

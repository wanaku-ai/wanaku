import { Component, ErrorInfo, ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { InlineNotification, Button } from "@carbon/react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

function DashboardButton({ onReset }: { onReset: () => void }) {
  const navigate = useNavigate();
  return (
    <Button
      kind="primary"
      style={{ marginTop: "1rem" }}
      onClick={() => {
        onReset();
        navigate("/");
      }}
    >
      Go to Dashboard
    </Button>
  );
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
          <DashboardButton onReset={() => this.setState({ hasError: false, error: null })} />
        </div>
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;

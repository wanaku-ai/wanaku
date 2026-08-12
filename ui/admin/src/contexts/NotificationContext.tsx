import { createContext, useContext, useState, useCallback, type ReactNode } from "react";
import { ToastNotification } from "@carbon/react";

interface Notification {
  id: string;
  title?: string;
  text: string;
  kind: "info" | "success" | "warning" | "error";
  timeout: number;
}

interface NotificationContextValue {
  show(msg: { title?: string; text: string; kind?: Notification["kind"]; timeout?: number }): void;
  dismiss(id: string): void;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

let notificationIdCounter = 0;

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const show = useCallback((msg: { title?: string; text: string; kind?: Notification["kind"]; timeout?: number }) => {
    const id = `notification-${++notificationIdCounter}`;
    const notification: Notification = {
      id,
      title: msg.title,
      text: msg.text,
      kind: msg.kind || "info",
      timeout: msg.timeout || 10000,
    };
    setNotifications(prev => [...prev, notification]);

    // Auto-dismiss after timeout
    setTimeout(() => {
      setNotifications(prev => prev.filter(n => n.id !== id));
    }, notification.timeout);
  }, []);

  const dismiss = useCallback((id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id));
  }, []);

  return (
    <NotificationContext.Provider value={{ show, dismiss }}>
      {children}
      <div
        style={{
          position: "fixed",
          top: "3rem",
          right: "1rem",
          zIndex: 9000,
          display: "flex",
          flexDirection: "column",
          gap: "0.5rem",
        }}
      >
        {notifications.map(notification => (
          <ToastNotification
            key={notification.id}
            kind={notification.kind}
            title={notification.title || notification.kind.charAt(0).toUpperCase() + notification.kind.slice(1)}
            subtitle={notification.text}
            onCloseButtonClick={() => dismiss(notification.id)}
            timeout={notification.timeout}
          />
        ))}
      </div>
    </NotificationContext.Provider>
  );
}

export function useNotifications(): NotificationContextValue {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error("useNotifications must be used within a NotificationProvider");
  }
  return context;
}

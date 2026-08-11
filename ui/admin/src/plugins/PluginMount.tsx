import { useRef, useEffect } from "react";
import type { Disposable } from "./types";

interface PluginMountProps {
  mount: (container: HTMLElement) => void | Disposable;
  pluginId: string;
}

export function PluginMount({ mount, pluginId }: PluginMountProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const disposableRef = useRef<Disposable | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    try {
      const result = mount(container);
      if (result && typeof result.dispose === "function") {
        disposableRef.current = result;
      }
    } catch (err) {
      console.error(`Plugin "${pluginId}" mount failed:`, err);
    }

    return () => {
      try {
        disposableRef.current?.dispose();
      } catch (err) {
        console.error(`Plugin "${pluginId}" dispose failed:`, err);
      }
      disposableRef.current = null;
    };
  }, [mount, pluginId]);

  return <div ref={containerRef} data-plugin-id={pluginId} />;
}

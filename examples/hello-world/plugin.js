export async function activate(host) {
  host.navigation.add({
    id: "hello",
    label: "Hello Plugin",
    route: "/hello",
    order: 100,
  });

  host.pages.register({
    route: "/hello",
    mount(container) {
      container.innerHTML = `
        <div style="padding: 2rem;">
          <h2>Hello from Plugin!</h2>
          <p>This page was contributed by the <strong>hello-world</strong> plugin.</p>
          <p>Host API version: <code>${host.version}</code></p>
        </div>
      `;
      return {
        dispose() {
          container.innerHTML = "";
        },
      };
    },
  });
}

export function deactivate() {}

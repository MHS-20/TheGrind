// Loaded after mermaid.min.js; the theme emits plain deferred scripts, so no ES modules here.
const dark = window.matchMedia("(prefers-color-scheme: dark)").matches;
mermaid.initialize({ startOnLoad: false, theme: dark ? "dark" : "default" });
mermaid.run({ querySelector: ".mermaid" });

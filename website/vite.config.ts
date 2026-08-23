import {defineConfig, loadEnv, type Plugin} from 'vite'
import react from '@vitejs/plugin-react'

function googleAnalyticsPlugin(mode: string): Plugin
{
  const analyticsId = loadEnv(mode, process.cwd(), "VITE_").VITE_GOOGLE_ANALYTICS_ID;
  const analyticsMarkup = mode === "production" && analyticsId
    ? `<!-- Google tag (gtag.js) -->
    <script async src="https://www.googletagmanager.com/gtag/js?id=${analyticsId}"></script>
    <script>
      window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());

      gtag('config', '${analyticsId}');
    </script>`
    : "";

  return {
    name: "docuhyphen-google-analytics",
    transformIndexHtml(html)
    {
      return html.replace("<!--docuhyphen-analytics-->", analyticsMarkup);
    },
  };
}

// https://vite.dev/config/
export default defineConfig(({mode}) => ({
  plugins: [react(), googleAnalyticsPlugin(mode)],
  ssr: {
    noExternal: true,
  },
  server: {
    proxy: {
      // Local development: forward public API calls to the Quarkus backend so the
      // "Speak to Sales" form works without a VITE_API_BASE_URL override.
      "/no-auth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
}))

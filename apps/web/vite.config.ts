import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Sem PWA de propósito: o player do celular é o app Android, e o web é a mesa
// de controle (biblioteca e playlists), aberta no navegador.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3510,
    strictPort: true,
    // Em desenvolvimento a API roda na 3501 (ver apps/api/src/server.ts).
    proxy: { "/api": "http://127.0.0.1:3501" },
  },
});

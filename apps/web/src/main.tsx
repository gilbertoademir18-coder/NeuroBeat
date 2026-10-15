import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import "./app.css";
import { Layout } from "./layout.tsx";
import { Biblioteca } from "./paginas/biblioteca.tsx";
import { Playlist } from "./paginas/playlist.tsx";
import { Playlists } from "./paginas/playlists.tsx";

const roteador = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [
      { index: true, element: <Biblioteca /> },
      { path: "playlists", element: <Playlists /> },
      { path: "playlists/:id", element: <Playlist /> },
    ],
  },
]);

createRoot(document.getElementById("raiz")!).render(
  <StrictMode>
    <RouterProvider router={roteador} />
  </StrictMode>,
);

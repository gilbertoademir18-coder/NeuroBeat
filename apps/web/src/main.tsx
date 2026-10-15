import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import "./app.css";
import { Layout } from "./layout.tsx";
import { Biblioteca } from "./paginas/biblioteca.tsx";

/*
 * A tela de playlists (editor com ordem e loop) vem a seguir e vira um ramo
 * aqui, com um link no cabeçalho.
 */
const roteador = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [{ index: true, element: <Biblioteca /> }],
  },
]);

createRoot(document.getElementById("raiz")!).render(
  <StrictMode>
    <RouterProvider router={roteador} />
  </StrictMode>,
);

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import "./app.css";
import { Layout } from "./layout.tsx";
import { Inicio } from "./paginas/inicio.tsx";

/*
 * As telas que vêm por aí: biblioteca (músicas por raiz, busca) e playlists
 * (editor com ordem e loop). Cada uma vira um ramo aqui.
 */
const roteador = createBrowserRouter([
  {
    path: "/",
    element: <Layout />,
    children: [{ index: true, element: <Inicio /> }],
  },
]);

createRoot(document.getElementById("raiz")!).render(
  <StrictMode>
    <RouterProvider router={roteador} />
  </StrictMode>,
);

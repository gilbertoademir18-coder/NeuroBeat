/**
 * Gera os ícones do app a partir de uma única definição em SVG.
 *
 * Rode com `npm run icons` sempre que mudar a marca. Os arquivos vão para
 * `public/` com nomes fixos, porque o ícone da bandeja (`scripts/tray.ps1`, na
 * raiz) aponta para o `icon-192.png` pelo nome.
 */
import { mkdir, writeFile } from "node:fs/promises";
import sharp from "sharp";

const PASTA = new URL("../public/", import.meta.url);

/**
 * A marca: uma linha de batimento (o "beat") sobre um fundo violeta. Simples
 * o bastante para continuar legível nos 32×32 da bandeja.
 *
 * `margem` encolhe o desenho para dentro, para os usos que recortam o ícone
 * em círculo (o ícone adaptativo do Android, por exemplo).
 */
function svg(margem = 0) {
  const escala = 1 - margem * 2;
  const desloca = 256 * margem * 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="fundo" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#8b5cf6"/>
      <stop offset="1" stop-color="#5b21b6"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="112" fill="url(#fundo)"/>
  <g transform="translate(${desloca} ${desloca}) scale(${escala})">
    <path d="M88 268 H184 L224 160 L288 360 L328 268 H424" fill="none" stroke="#f8f7fb"
          stroke-width="44" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
}

const ALVOS = [
  { arquivo: "icon-192.png", tamanho: 192, margem: 0 },
  { arquivo: "icon-512.png", tamanho: 512, margem: 0 },
];

await mkdir(PASTA, { recursive: true });
await writeFile(new URL("favicon.svg", PASTA), svg());
console.log("✓ favicon.svg");

for (const { arquivo, tamanho, margem } of ALVOS) {
  const png = await sharp(Buffer.from(svg(margem))).resize(tamanho, tamanho).png().toBuffer();
  await writeFile(new URL(arquivo, PASTA), png);
  console.log(`✓ ${arquivo} (${tamanho}×${tamanho}, ${png.length} bytes)`);
}

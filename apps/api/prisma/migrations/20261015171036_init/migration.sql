-- CreateTable
CREATE TABLE "raizes" (
    "id" SERIAL NOT NULL,
    "alias" TEXT NOT NULL,
    "caminho" TEXT NOT NULL,
    "criada_em" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "raizes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "musicas" (
    "id" SERIAL NOT NULL,
    "raiz_id" INTEGER NOT NULL,
    "caminho" TEXT NOT NULL,
    "tamanho" BIGINT NOT NULL,
    "impressao" TEXT NOT NULL,
    "titulo" TEXT,
    "artista" TEXT,
    "album" TEXT,
    "duracao_ms" INTEGER,
    "vista_em" TIMESTAMP(3) NOT NULL,
    "sumida_em" TIMESTAMP(3),
    "criada_em" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "musicas_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "playlists" (
    "id" SERIAL NOT NULL,
    "nome" TEXT NOT NULL,
    "loop" BOOLEAN NOT NULL DEFAULT false,
    "criada_em" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "atualizada_em" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "playlists_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "playlist_itens" (
    "id" SERIAL NOT NULL,
    "playlist_id" INTEGER NOT NULL,
    "musica_id" INTEGER NOT NULL,
    "posicao" INTEGER NOT NULL,

    CONSTRAINT "playlist_itens_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "raizes_alias_key" ON "raizes"("alias");

-- CreateIndex
CREATE INDEX "musicas_impressao_idx" ON "musicas"("impressao");

-- CreateIndex
CREATE UNIQUE INDEX "musicas_raiz_id_caminho_key" ON "musicas"("raiz_id", "caminho");

-- CreateIndex
CREATE INDEX "playlist_itens_playlist_id_posicao_idx" ON "playlist_itens"("playlist_id", "posicao");

-- AddForeignKey
ALTER TABLE "musicas" ADD CONSTRAINT "musicas_raiz_id_fkey" FOREIGN KEY ("raiz_id") REFERENCES "raizes"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "playlist_itens" ADD CONSTRAINT "playlist_itens_playlist_id_fkey" FOREIGN KEY ("playlist_id") REFERENCES "playlists"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "playlist_itens" ADD CONSTRAINT "playlist_itens_musica_id_fkey" FOREIGN KEY ("musica_id") REFERENCES "musicas"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

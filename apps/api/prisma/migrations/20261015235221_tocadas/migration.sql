-- CreateTable
CREATE TABLE "tocadas" (
    "id" TEXT NOT NULL,
    "impressao" TEXT NOT NULL,
    "tocada_em" TIMESTAMP(3) NOT NULL,
    "recebida_em" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "tocadas_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "tocadas_impressao_idx" ON "tocadas"("impressao");

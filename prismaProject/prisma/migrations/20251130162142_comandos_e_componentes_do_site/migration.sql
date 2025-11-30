-- CreateTable
CREATE TABLE "Command" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "subCommandGroup" TEXT,
    "subCommand" TEXT,
    "discordId" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "explanation" JSONB NOT NULL,
    "isEnabled" BOOLEAN NOT NULL DEFAULT true,
    "category" TEXT NOT NULL,

    CONSTRAINT "Command_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PageComponent" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "components" JSONB NOT NULL,

    CONSTRAINT "PageComponent_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "Command_discordId_key" ON "Command"("discordId");

-- CreateIndex
CREATE UNIQUE INDEX "Command_name_subCommandGroup_subCommand_key" ON "Command"("name", "subCommandGroup", "subCommand");

-- CreateIndex
CREATE UNIQUE INDEX "PageComponent_name_key" ON "PageComponent"("name");

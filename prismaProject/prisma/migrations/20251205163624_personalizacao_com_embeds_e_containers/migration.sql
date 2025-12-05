-- CreateEnum
CREATE TYPE "ButtonStyle" AS ENUM ('PRIMARY', 'SECONDARY', 'SUCCESS', 'DANGER', 'LINK');

-- CreateEnum
CREATE TYPE "ActionRowComponentType" AS ENUM ('BUTTON', 'SELECT_MENU');

-- CreateTable
CREATE TABLE "Embed" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "title" TEXT,
    "description" TEXT,
    "colorHex" TEXT,
    "authorName" TEXT,
    "authorAvatar" TEXT,
    "thumbnail" TEXT,
    "image" TEXT,
    "footerContent" TEXT,
    "footerImage" TEXT,
    "fields" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "createdByUserId" TEXT NOT NULL,
    "identification" TEXT NOT NULL,
    "messageId" INTEGER,

    CONSTRAINT "Embed_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Container" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,

    CONSTRAINT "Container_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContainerComponent" (
    "id" SERIAL NOT NULL,
    "containerId" INTEGER NOT NULL,
    "isActionRow" BOOLEAN NOT NULL DEFAULT false,
    "actionRowId" INTEGER,
    "details" JSONB,

    CONSTRAINT "ContainerComponent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActionRow" (
    "id" SERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "embedId" INTEGER,
    "messageId" INTEGER,
    "containerComponentId" INTEGER,

    CONSTRAINT "ActionRow_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActionRowComponent" (
    "id" SERIAL NOT NULL,
    "actionRowId" INTEGER NOT NULL,
    "type" "ActionRowComponentType" NOT NULL,
    "customId" TEXT NOT NULL,

    CONSTRAINT "ActionRowComponent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActionRowButton" (
    "id" SERIAL NOT NULL,
    "actionRowComponentId" INTEGER NOT NULL,
    "label" TEXT NOT NULL,
    "url" TEXT,
    "disabled" BOOLEAN NOT NULL DEFAULT false,
    "emoji" TEXT,
    "style" "ButtonStyle" NOT NULL,

    CONSTRAINT "ActionRowButton_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActionRowSelect" (
    "id" SERIAL NOT NULL,
    "actionRowComponentId" INTEGER NOT NULL,
    "placeholder" TEXT NOT NULL,
    "minValues" INTEGER NOT NULL,
    "maxValues" INTEGER NOT NULL,
    "disabled" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "ActionRowSelect_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ActionRowSelectOption" (
    "id" SERIAL NOT NULL,
    "actionRowSelectId" INTEGER NOT NULL,
    "label" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "emoji" TEXT,
    "default" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "ActionRowSelectOption_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Message" (
    "id" SERIAL NOT NULL,
    "isComponentsV2" BOOLEAN NOT NULL DEFAULT false,
    "componentsJson" JSONB,

    CONSTRAINT "Message_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "ActionRow_containerComponentId_key" ON "ActionRow"("containerComponentId");

-- CreateIndex
CREATE UNIQUE INDEX "ActionRowButton_actionRowComponentId_key" ON "ActionRowButton"("actionRowComponentId");

-- CreateIndex
CREATE UNIQUE INDEX "ActionRowSelect_actionRowComponentId_key" ON "ActionRowSelect"("actionRowComponentId");

-- AddForeignKey
ALTER TABLE "Embed" ADD CONSTRAINT "Embed_messageId_fkey" FOREIGN KEY ("messageId") REFERENCES "Message"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ContainerComponent" ADD CONSTRAINT "ContainerComponent_containerId_fkey" FOREIGN KEY ("containerId") REFERENCES "Container"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRow" ADD CONSTRAINT "ActionRow_embedId_fkey" FOREIGN KEY ("embedId") REFERENCES "Embed"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRow" ADD CONSTRAINT "ActionRow_messageId_fkey" FOREIGN KEY ("messageId") REFERENCES "Message"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRow" ADD CONSTRAINT "ActionRow_containerComponentId_fkey" FOREIGN KEY ("containerComponentId") REFERENCES "ContainerComponent"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRowComponent" ADD CONSTRAINT "ActionRowComponent_actionRowId_fkey" FOREIGN KEY ("actionRowId") REFERENCES "ActionRow"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRowButton" ADD CONSTRAINT "ActionRowButton_actionRowComponentId_fkey" FOREIGN KEY ("actionRowComponentId") REFERENCES "ActionRowComponent"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRowSelect" ADD CONSTRAINT "ActionRowSelect_actionRowComponentId_fkey" FOREIGN KEY ("actionRowComponentId") REFERENCES "ActionRowComponent"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ActionRowSelectOption" ADD CONSTRAINT "ActionRowSelectOption_actionRowSelectId_fkey" FOREIGN KEY ("actionRowSelectId") REFERENCES "ActionRowSelect"("id") ON DELETE CASCADE ON UPDATE CASCADE;

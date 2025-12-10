/*
  Warnings:

  - You are about to drop the column `containerId` on the `Giveaway` table. All the data in the column will be lost.
  - You are about to drop the column `embedId` on the `Giveaway` table. All the data in the column will be lost.
  - You are about to drop the column `personalizedMessageId` on the `Giveaway` table. All the data in the column will be lost.
  - You are about to drop the `ActionRow` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ActionRowButton` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ActionRowComponent` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ActionRowSelect` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ActionRowSelectOption` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Container` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `ContainerComponent` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Embed` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Message` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE "ActionRow" DROP CONSTRAINT "ActionRow_containerComponentId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRow" DROP CONSTRAINT "ActionRow_embedId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRow" DROP CONSTRAINT "ActionRow_messageId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRowButton" DROP CONSTRAINT "ActionRowButton_actionRowComponentId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRowComponent" DROP CONSTRAINT "ActionRowComponent_actionRowId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRowSelect" DROP CONSTRAINT "ActionRowSelect_actionRowComponentId_fkey";

-- DropForeignKey
ALTER TABLE "ActionRowSelectOption" DROP CONSTRAINT "ActionRowSelectOption_actionRowSelectId_fkey";

-- DropForeignKey
ALTER TABLE "ContainerComponent" DROP CONSTRAINT "ContainerComponent_containerId_fkey";

-- DropForeignKey
ALTER TABLE "Embed" DROP CONSTRAINT "Embed_messageId_fkey";

-- AlterTable
ALTER TABLE "Giveaway" DROP COLUMN "containerId",
DROP COLUMN "embedId",
DROP COLUMN "personalizedMessageId";

-- DropTable
DROP TABLE "ActionRow";

-- DropTable
DROP TABLE "ActionRowButton";

-- DropTable
DROP TABLE "ActionRowComponent";

-- DropTable
DROP TABLE "ActionRowSelect";

-- DropTable
DROP TABLE "ActionRowSelectOption";

-- DropTable
DROP TABLE "Container";

-- DropTable
DROP TABLE "ContainerComponent";

-- DropTable
DROP TABLE "Embed";

-- DropTable
DROP TABLE "Message";

-- DropEnum
DROP TYPE "ActionRowComponentType";

-- DropEnum
DROP TYPE "ButtonStyle";

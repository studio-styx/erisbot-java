/*
  Warnings:

  - You are about to drop the column `expiresAt` on the `UserToken` table. All the data in the column will be lost.
  - You are about to drop the column `tokenHash` on the `UserToken` table. All the data in the column will be lost.
  - A unique constraint covering the columns `[access_token]` on the table `UserToken` will be added. If there are existing duplicate values, this will fail.
  - A unique constraint covering the columns `[refresh_token]` on the table `UserToken` will be added. If there are existing duplicate values, this will fail.
  - Added the required column `access_token` to the `UserToken` table without a default value. This is not possible if the table is not empty.
  - Added the required column `expiresIn` to the `UserToken` table without a default value. This is not possible if the table is not empty.
  - Added the required column `refresh_token` to the `UserToken` table without a default value. This is not possible if the table is not empty.
  - Added the required column `token_type` to the `UserToken` table without a default value. This is not possible if the table is not empty.

*/
-- DropIndex
DROP INDEX "UserToken_tokenHash_key";

-- AlterTable
ALTER TABLE "UserToken" DROP COLUMN "expiresAt",
DROP COLUMN "tokenHash",
ADD COLUMN     "access_token" TEXT NOT NULL,
ADD COLUMN     "expiresIn" TIMESTAMP(3) NOT NULL,
ADD COLUMN     "refresh_token" TEXT NOT NULL,
ADD COLUMN     "token_type" TEXT NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "UserToken_access_token_key" ON "UserToken"("access_token");

-- CreateIndex
CREATE UNIQUE INDEX "UserToken_refresh_token_key" ON "UserToken"("refresh_token");

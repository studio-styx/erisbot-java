/*
  Warnings:

  - You are about to drop the `CombatHistory` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `CombatPowerHistory` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `PetPower` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `PetPowerEffectiveness` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `Stock` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `StockHistory` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `StockHolding` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `UserPetPower` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_loserPetId_fkey";

-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_pet1Id_fkey";

-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_pet2Id_fkey";

-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_user1Id_fkey";

-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_user2Id_fkey";

-- DropForeignKey
ALTER TABLE "CombatHistory" DROP CONSTRAINT "CombatHistory_winnerPetId_fkey";

-- DropForeignKey
ALTER TABLE "CombatPowerHistory" DROP CONSTRAINT "CombatPowerHistory_combatId_fkey";

-- DropForeignKey
ALTER TABLE "CombatPowerHistory" DROP CONSTRAINT "CombatPowerHistory_petId_fkey";

-- DropForeignKey
ALTER TABLE "CombatPowerHistory" DROP CONSTRAINT "CombatPowerHistory_userPetPowerId_fkey";

-- DropForeignKey
ALTER TABLE "PetPowerEffectiveness" DROP CONSTRAINT "PetPowerEffectiveness_fromPowerId_fkey";

-- DropForeignKey
ALTER TABLE "PetPowerEffectiveness" DROP CONSTRAINT "PetPowerEffectiveness_toPowerId_fkey";

-- DropForeignKey
ALTER TABLE "StockHistory" DROP CONSTRAINT "StockHistory_stockId_fkey";

-- DropForeignKey
ALTER TABLE "StockHolding" DROP CONSTRAINT "StockHolding_stockId_fkey";

-- DropForeignKey
ALTER TABLE "StockHolding" DROP CONSTRAINT "StockHolding_userId_fkey";

-- DropForeignKey
ALTER TABLE "UserPetPower" DROP CONSTRAINT "UserPetPower_powerId_fkey";

-- DropForeignKey
ALTER TABLE "UserPetPower" DROP CONSTRAINT "UserPetPower_userPetId_fkey";

-- DropTable
DROP TABLE "CombatHistory";

-- DropTable
DROP TABLE "CombatPowerHistory";

-- DropTable
DROP TABLE "PetPower";

-- DropTable
DROP TABLE "PetPowerEffectiveness";

-- DropTable
DROP TABLE "Stock";

-- DropTable
DROP TABLE "StockHistory";

-- DropTable
DROP TABLE "StockHolding";

-- DropTable
DROP TABLE "UserPetPower";

-- DropEnum
DROP TYPE "PetElement";

-- DropEnum
DROP TYPE "PetPowerType";

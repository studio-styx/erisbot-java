package studio.styx.erisbot.utils;

import org.jooq.DSLContext;
import studio.styx.erisbot.jooq.tables.records.UserRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static studio.styx.erisbot.jooq.tables.User.USER;

public class Utils {
    public static UserRecord getOrCreateUser(DSLContext tx, String userId) {
        return tx.insertInto(USER)
                .columns(USER.ID, USER.MONEY, USER.CREATEDAT, USER.UPDATEDAT)
                .values(userId, BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now())
                .onConflict(USER.ID)
                .doUpdate()
                .set(USER.UPDATEDAT, LocalDateTime.now()) // atualiza timestamp sempre
                .returning(USER.fields())
                .fetchOne(); // ← SEMPRE retorna o usuário!
    }

    public static int getRandomInt(int min, int max) {
        return (int) (Math.random() * (max - min + 1) + min);
    }

    public static double getRandomDouble(double min, double max) {
        return Math.random() * (max - min) + min;
    }

    public static String formatMoney(BigDecimal value) {
        return String.format("%,d", value.longValue()); // vira 1.234.567
    }

    public static String formatNumber(long number) {
        return String.format("%,d", number).replace(",", ".");
    }

    public static String formatNumber(double number) {
        return String.format("%,.0f", number).replace(",", ".");
    }
}

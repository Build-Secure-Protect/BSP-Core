package com.mrgregles.bsp_core.bank;

/**
 * What a bank operation came back with. {@code key} is a lang key under {@code message.bsp_core.bank.}
 * naming the outcome ("ok", "short", "refused", "unreachable", ...); {@code pieces} is the balance
 * after the operation when it is known.
 */
public record BankResult(boolean ok, String key, long pieces) {
    public static BankResult ok(long pieces) {
        return new BankResult(true, "ok", pieces);
    }

    public static BankResult fail(String key) {
        return new BankResult(false, key, -1);
    }
}

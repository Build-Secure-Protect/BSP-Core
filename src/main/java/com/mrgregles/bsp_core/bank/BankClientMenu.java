package com.mrgregles.bsp_core.bank;

import java.util.List;

/** A menu whose client side takes the bank's data packets: the banking screen and the deposit box screen. */
public interface BankClientMenu {
    void receive(BankAccount account, List<BankTransaction> log, List<String> found, String messageKey, String[] messageArgs);
}

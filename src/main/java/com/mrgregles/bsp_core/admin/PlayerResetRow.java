package com.mrgregles.bsp_core.admin;

import java.util.UUID;

/** One admin reset of a player ({@code /bsp player reset}): who, by what name, when (wall clock, ms) and by whom. */
public record PlayerResetRow(UUID id, String name, long resetMs, String by) {
}

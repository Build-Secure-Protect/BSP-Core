package com.mrgregles.bsp_core.shop;

/** A block entity that runs a shop with opening hours: the Vendor Post today, the Market Stall when its models arrive. */
public interface Assignable {
    void assign(String shopId, OpenHours hours, String mob, String mobNbt);
}

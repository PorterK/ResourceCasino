package net.kalob.ResourceCasino;

/** Where the bar sits on the casino floor (east side, south of the big wheel). Purely for looks. */
final class Bar {

    /** The counter runs north-south at this x; the bartender stands behind it. */
    static final int COUNTER_X = CasinoWorld.HALF - 6;
    static final int CENTER_Z = 22;
    static final int HALF_LENGTH = 5;
    static final double BARTENDER_X = COUNTER_X + 2.5;
    static final int SHELF_X = CasinoWorld.HALF - 2;

    private Bar() {}
}

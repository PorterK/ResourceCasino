package net.kalob.ResourceCasino;

/** Running totals of what players have bet and what the house has paid back, per resource. */
public final class HouseLedger {

    private final long[] wagered = new long[Resource.values().length];
    private final long[] paid = new long[Resource.values().length];
    private final long[] rounds = new long[Resource.values().length];

    public void wager(Resource resource, long stake) {
        wagered[resource.ordinal()] += stake;
        rounds[resource.ordinal()]++;
    }

    public void pay(Resource resource, long payout) {
        paid[resource.ordinal()] += payout;
    }

    public long wagered(Resource resource) { return wagered[resource.ordinal()]; }

    public long paid(Resource resource) { return paid[resource.ordinal()]; }

    public long rounds(Resource resource) { return rounds[resource.ordinal()]; }

    /** House profit so far (positive means the house is ahead). */
    public long profit(Resource resource) { return wagered(resource) - paid(resource); }

    public void load(Resource resource, long wageredTotal, long paidTotal, long roundCount) {
        wagered[resource.ordinal()] = wageredTotal;
        paid[resource.ordinal()] = paidTotal;
        rounds[resource.ordinal()] = roundCount;
    }
}

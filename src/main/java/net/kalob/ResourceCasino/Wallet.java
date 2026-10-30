package net.kalob.ResourceCasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's casino balance, one counter per resource. Players deposit real
 * items at the cashier, play from this balance, and withdraw real items back out.
 */
public final class Wallet {

    private final Map<UUID, long[]> balances = new HashMap<>();
    private boolean dirty;

    public long balance(UUID player, Resource resource) {
        long[] row = balances.get(player);
        return row == null ? 0 : row[resource.ordinal()];
    }

    public void deposit(UUID player, Resource resource, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative deposit: " + amount);
        if (amount == 0) return;
        balances.computeIfAbsent(player, k -> new long[Resource.values().length])[resource.ordinal()] += amount;
        dirty = true;
    }

    /** Takes {@code amount} out of the balance. Returns false (and changes nothing) if there isn't enough. */
    public boolean withdraw(UUID player, Resource resource, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative withdrawal: " + amount);
        if (amount == 0) return true;
        long[] row = balances.get(player);
        if (row == null || row[resource.ordinal()] < amount) return false;
        row[resource.ordinal()] -= amount;
        dirty = true;
        return true;
    }

    /** Sets a balance directly (used when loading saved data). */
    public void set(UUID player, Resource resource, long amount) {
        balances.computeIfAbsent(player, k -> new long[Resource.values().length])[resource.ordinal()] = Math.max(0, amount);
        dirty = true;
    }

    public Map<UUID, long[]> snapshot() {
        Map<UUID, long[]> copy = new HashMap<>();
        balances.forEach((id, row) -> copy.put(id, row.clone()));
        return copy;
    }

    public void clear() {
        balances.clear();
        dirty = true;
    }

    public boolean dirty() { return dirty; }

    public void markClean() { dirty = false; }
}

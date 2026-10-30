package net.kalob.ResourceCasino;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    @Test
    void resourcesAreSeparateCurrencies() {
        Wallet wallet = new Wallet();
        UUID id = UUID.randomUUID();
        wallet.deposit(id, Resource.IRON, 10);
        assertEquals(10, wallet.balance(id, Resource.IRON));
        assertEquals(0, wallet.balance(id, Resource.DIAMOND));
        assertFalse(wallet.withdraw(id, Resource.DIAMOND, 1));
        assertFalse(wallet.withdraw(id, Resource.IRON, 11));
        assertEquals(10, wallet.balance(id, Resource.IRON));
        assertTrue(wallet.withdraw(id, Resource.IRON, 10));
        assertEquals(0, wallet.balance(id, Resource.IRON));
    }

    @Test
    void negativeAmountsAreRejected() {
        Wallet wallet = new Wallet();
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> wallet.deposit(id, Resource.IRON, -1));
        assertThrows(IllegalArgumentException.class, () -> wallet.withdraw(id, Resource.IRON, -1));
    }

    @Test
    void blocksCountAsNine() {
        assertEquals(9, Resource.unitsOf(Resource.DIAMOND, "DIAMOND_BLOCK"));
        assertEquals(1, Resource.unitsOf(Resource.NETHERITE, "NETHERITE_INGOT"));
        assertEquals(Resource.COPPER, Resource.ofMaterial("COPPER_BLOCK"));
        assertNull(Resource.ofMaterial("GOLD_INGOT"));
        assertNull(Resource.ofMaterial("NETHERITE_SCRAP"));
    }

    @Test
    void dataSurvivesASaveAndLoad() throws Exception {
        CasinoData data = new CasinoData();
        UUID id = UUID.randomUUID();
        data.wallet().deposit(id, Resource.EMERALD, 42);
        data.ledger().wager(Resource.EMERALD, 5);
        data.ledger().pay(Resource.EMERALD, 3);
        data.setBuilt(true);
        CasinoData copy = new CasinoData();
        copy.loadFromString(data.saveToString());
        assertEquals(42, copy.wallet().balance(id, Resource.EMERALD));
        assertEquals(2, copy.ledger().profit(Resource.EMERALD));
        assertTrue(copy.built());
        assertFalse(copy.dirty());
    }
}

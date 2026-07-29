package fr.fidelmobs.defis;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur permettant de reconnaître de façon fiable le menu /defi lors d'un
 * InventoryClickEvent, plutôt que de comparer le titre affiché.
 */
public class DefiInventoryHolder implements InventoryHolder {

    private Inventory inventory;
    private boolean quotidien;
    private int page;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isQuotidien() {
        return quotidien;
    }

    public void setQuotidien(boolean quotidien) {
        this.quotidien = quotidien;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}

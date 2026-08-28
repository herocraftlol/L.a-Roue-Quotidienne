package fr.fidelmobs.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur permettant de reconnaître de façon fiable le menu /shop lors d'un
 * InventoryClickEvent, plutôt que de comparer le titre affiché.
 */
public class ShopInventoryHolder implements InventoryHolder {

    private Inventory inventory;
    private int onglet;
    private int page;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public int getOnglet() {
        return onglet;
    }

    public void setOnglet(int onglet) {
        this.onglet = onglet;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}

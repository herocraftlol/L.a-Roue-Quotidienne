package fr.fidelmobs.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur permettant de reconnaître de façon fiable le menu de choix d'équipement/armes
 * (armure, épée, flèches) lors d'un InventoryClickEvent, plutôt que de comparer le titre affiché.
 */
public class GearSelectorInventoryHolder implements InventoryHolder {

    private Inventory inventory;
    private int page;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}

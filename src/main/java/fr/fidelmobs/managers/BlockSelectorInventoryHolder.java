package fr.fidelmobs.managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marqueur permettant de reconnaître de façon fiable le menu de sélection du bloc de
 * construction actif lors d'un InventoryClickEvent, plutôt que de comparer le titre affiché.
 */
public class BlockSelectorInventoryHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}

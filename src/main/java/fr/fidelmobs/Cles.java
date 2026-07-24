package fr.fidelmobs;

import org.bukkit.NamespacedKey;

public final class Cles {

    private Cles() {
    }

    public static final NamespacedKey RARETE = new NamespacedKey("fidelmobs", "rarete");
    public static final NamespacedKey ENCHANTE = new NamespacedKey("fidelmobs", "enchante");
    public static final NamespacedKey KIT_VERROUILLE = new NamespacedKey("fidelmobs", "kit_verrouille");
    public static final NamespacedKey CHARGE_BLOC = new NamespacedKey("fidelmobs", "charge_bloc");
    public static final NamespacedKey BLOC_POSE = new NamespacedKey("fidelmobs", "bloc_pose");
    public static final NamespacedKey INVOCATION = new NamespacedKey("fidelmobs", "invocation");
    public static final NamespacedKey INVOCATION_TYPE = new NamespacedKey("fidelmobs", "invocation_type");
    public static final NamespacedKey BLOC_SELECTEUR = new NamespacedKey("fidelmobs", "bloc_selecteur");
    public static final NamespacedKey BLOC_CHOIX = new NamespacedKey("fidelmobs", "bloc_choix");

    // Sélecteur d'équipement (GUI armes/armure/flèches, avant-avant-dernier slot de la hotbar)
    public static final NamespacedKey EQUIPEMENT_SELECTEUR = new NamespacedKey("fidelmobs", "equipement_selecteur");
    public static final NamespacedKey EQUIPEMENT_CHOIX_INDEX = new NamespacedKey("fidelmobs", "equipement_choix_index");
    public static final NamespacedKey EQUIPEMENT_CHOIX_CATEGORIE = new NamespacedKey("fidelmobs", "equipement_choix_categorie");

    // Flèches à effet (collection obtenue à la roue, équipables/tirables avec l'arc du kit)
    public static final NamespacedKey FLECHE_RARETE = new NamespacedKey("fidelmobs", "fleche_rarete");
    public static final NamespacedKey FLECHE_MARQUEUR = new NamespacedKey("fidelmobs", "fleche_marqueur");

    // Arc du kit (4e slot de la hotbar)
    public static final NamespacedKey ARC_KIT = new NamespacedKey("fidelmobs", "arc_kit");

    // Pouvoirs spéciaux (collection obtenue à la roue, catégorie "Pouvoir")
    // Sélecteur de pouvoir (GUI, avant-avant-avant-dernier slot de la hotbar, 6e sur 9)
    public static final NamespacedKey POUVOIR_SELECTEUR = new NamespacedKey("fidelmobs", "pouvoir_selecteur");
    public static final NamespacedKey POUVOIR_CHOIX_INDEX = new NamespacedKey("fidelmobs", "pouvoir_choix_index");
    // Item actif représentant le pouvoir actuellement équipé (5e slot de la hotbar)
    public static final NamespacedKey POUVOIR_ACTIF = new NamespacedKey("fidelmobs", "pouvoir_actif");
    public static final NamespacedKey POUVOIR_ID = new NamespacedKey("fidelmobs", "pouvoir_id");
}

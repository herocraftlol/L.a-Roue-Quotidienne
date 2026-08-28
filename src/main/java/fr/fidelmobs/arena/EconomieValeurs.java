package fr.fidelmobs.arena;

import fr.fidelmobs.mobs.MobRarity;

/**
 * Valeur de base en points de fidélité d'un objet/mob/pouvoir/bloc selon sa rareté,
 * partagée entre le système de sacrifice (valeur obtenue en le rendant) et la boutique
 * (prix = cette valeur × un multiplicateur), pour garder un rapport logique et cohérent
 * entre les deux. Calée sur le prix d'un ticket de roue (1500 points, voir
 * arene.cout-ticket-points) : un objet Légendaire vaut à peu près un ticket, un Commun une
 * fraction — la roue reste largement plus rentable que d'acheter/re-sacrifier en boucle.
 */
public final class EconomieValeurs {

    private EconomieValeurs() {
    }

    public static int valeurBase(MobRarity rarete) {
        return switch (rarete) {
            case COMMUN -> 40;
            case PEU_COMMUN -> 120;
            case RARE -> 320;
            case EPIQUE -> 750;
            case LEGENDAIRE -> 1600;
        };
    }

    // La boutique vend à un prix largement supérieur à la valeur de sacrifice/collecte :
    // acheter directement doit toujours rester moins rentable que jouer/tourner la roue.
    private static final int MULTIPLICATEUR_BOUTIQUE = 5;

    public static int prixBoutique(MobRarity rarete) {
        return valeurBase(rarete) * MULTIPLICATEUR_BOUTIQUE;
    }
}

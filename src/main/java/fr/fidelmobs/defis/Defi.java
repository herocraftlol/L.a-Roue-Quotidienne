package fr.fidelmobs.defis;

import fr.fidelmobs.data.PlayerDataManager;
import fr.fidelmobs.mobs.MobRarity;
import org.bukkit.Material;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

/**
 * Définition d'un défi (achievement), global ou quotidien : sa difficulté (rareté),
 * son objectif chiffré, sa récompense, et la fonction qui calcule la progression
 * actuelle du joueur à partir des données déjà suivies par le plugin (ou d'un compteur
 * générique dédié quand aucune statistique existante ne convient).
 */
public record Defi(
        String id,
        String nom,
        String description,
        Material icone,
        MobRarity rarete,
        int objectif,
        int recompensePoints,
        int recompenseTickets,
        ToIntBiFunction<PlayerDataManager, UUID> progression,
        BiFunction<PlayerDataManager, UUID, String> texteProgressionPersonnalise
) {

    /** Progression actuelle du joueur, plafonnée à l'objectif pour un affichage propre. */
    public int progressionActuelle(PlayerDataManager data, UUID uuid) {
        return Math.min(objectif, Math.max(0, progression.applyAsInt(data, uuid)));
    }

    public boolean estComplete(PlayerDataManager data, UUID uuid) {
        return progression.applyAsInt(data, uuid) >= objectif;
    }

    /** Texte de progression affiché dans le lore : personnalisé si fourni, sinon "x/objectif". */
    public String texteProgression(PlayerDataManager data, UUID uuid) {
        if (texteProgressionPersonnalise != null) {
            return texteProgressionPersonnalise.apply(data, uuid);
        }
        return progressionActuelle(data, uuid) + " / " + objectif;
    }
}

package fr.fidelmobs.mobs;

public enum MobRarity {
    COMMUN("§7", "Commun", 55),
    PEU_COMMUN("§a", "Peu commun", 25),
    RARE("§9", "Rare", 12),
    EPIQUE("§5", "Épique", 6),
    LEGENDAIRE("§6", "Légendaire", 2);

    private final String couleur;
    private final String label;
    private final int poids;

    MobRarity(String couleur, String label, int poids) {
        this.couleur = couleur;
        this.label = label;
        this.poids = poids;
    }

    public String getCouleur() {
        return couleur;
    }

    public String getLabel() {
        return label;
    }

    public int getPoids() {
        return poids;
    }
}

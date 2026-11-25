package fr.evan.starrysky.telescope;

import javax.annotation.Nullable;

/**
 * Un astre identifiable à la longue-vue : une étoile brillante, une planète ou une comète.
 * <p>
 * Toutes les valeurs sont fixées à la génération (graine fixe, ou données des constellations et des planètes) :
 * un même astre a toujours le même nom et les mêmes caractéristiques.
 *
 * @param x             direction de l'astre dans le repère du ciel (vecteur unitaire, avec y et z)
 * @param name          nom propre (« Véga ») ou désignation de catalogue (« HIP 48213 »)
 * @param named         vrai pour les astres qui portent un nom propre
 * @param constellation constellation de la région du ciel où se trouve l'astre
 * @param spectralClass lettre de la classe spectrale (B, A, F, G, K ou M) ; espace pour une planète
 * @param subclass      chiffre de sous-classe (0 à 9)
 * @param magnitude     magnitude apparente (plus elle est petite, plus l'astre est brillant)
 * @param distanceLy    distance en années-lumière (étoiles)
 * @param temperatureK  température de surface en kelvins
 * @param color         couleur de l'astre (0xRRGGBB), utilisée pour l'interface
 * @param figure        figure de constellation dont l'étoile fait partie (traits à la longue-vue), sinon null
 * @param planetType    type de planète (« terrestrial », « gas_giant », « comet »), null pour une étoile
 * @param distanceAu    distance en unités astronomiques (planètes)
 */
public record CatalogStar(
        float x, float y, float z,
        String name,
        boolean named,
        String constellation,
        char spectralClass,
        int subclass,
        float magnitude,
        int distanceLy,
        int temperatureK,
        int color,
        @Nullable String figure,
        @Nullable String planetType,
        float distanceAu) {

    public boolean isPlanet() {
        return planetType != null;
    }

    /** Vrai pour une comète (un astre « du système solaire » comme les planètes, mais de passage). */
    public boolean isComet() {
        return "comet".equals(planetType);
    }
}

package fr.evan.starrysky.telescope;

/**
 * Une étoile brillante, assez lumineuse pour être identifiée à la longue-vue.
 * <p>
 * Toutes les valeurs sont calculées une seule fois, à la génération du champ d'étoiles, à partir de la graine fixe :
 * une même étoile a toujours le même nom et les mêmes caractéristiques.
 *
 * @param x            direction de l'étoile dans le repère du ciel (vecteur unitaire)
 * @param name         nom propre (« Véga ») ou désignation de catalogue (« HIP 48213 »)
 * @param named        vrai pour les étoiles qui portent un nom propre
 * @param constellation constellation de la région du ciel où se trouve l'étoile
 * @param spectralClass lettre de la classe spectrale (B, A, F, G ou K)
 * @param subclass     chiffre de sous-classe (0 à 9)
 * @param magnitude    magnitude apparente (plus elle est petite, plus l'étoile est brillante)
 * @param distanceLy   distance en années-lumière
 * @param temperatureK température de surface en kelvins
 * @param color        couleur de l'étoile (0xRRGGBB), utilisée pour l'interface
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
        int color) {
}

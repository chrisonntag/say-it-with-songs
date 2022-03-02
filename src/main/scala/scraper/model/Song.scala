package scraper.model

import org.apache.commons.lang3.StringUtils

/**
 * A song data class.
 *
 * @param title Title of the song.
 * @param url Canonical link to the main page of the song.
 */
case class Song(title: String, url: String) extends Serializable {
    override def toString: String = {
        title + " | " +
          url
    }

    /**
     * Returns the similarity of a given word to this songs title using the token-based Jaccard Index which is the ratio
     * of intersection over union.
     * @param word The word to compare the title with.
     * @return A value.
     */
    def getDistance(word: String): Double = {
        def jaccardIndex(word: String): Double = {
            val X: Set[String] = word.toLowerCase().trim.split(" ").toSet
            val Y: Set[String] = title.toLowerCase().trim.split(" ").toSet
            X.intersect(Y).size / (X.size + Y.size - X.intersect(Y).size).toDouble
        }

        StringUtils.getJaroWinklerDistance(word.toLowerCase(), title.toLowerCase())
    }


}

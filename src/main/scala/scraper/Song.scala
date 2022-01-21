package scraper

case class Song(title: String, url: String) {
    override def toString: String = {
        title + " | " +
          url
    }

    def jaccardIndex(word: String): Int = {
        val X: Set[String] = word.toLowerCase().trim.split(" ").toSet
        val Y: Set[String] = title.toLowerCase().trim.split(" ").toSet
        (X.intersect(Y)).size/X.size + Y.size - (X.intersect(Y)).size
    }
}

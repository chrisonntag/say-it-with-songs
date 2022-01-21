package scraper

import com.redis.RedisClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.annotation.tailrec


class Scraper(t: String) {

    var urlPrefix: String = "https://soundcloud.com"
    var text: String = t
    var limit: Int = Int.MaxValue
    var maxJaccard: Int = 3
    val redisClient = new RedisClient("localhost", 6379)

    def queryText(text: String): List[(String, Option[Song])] = {
        val words = text.split(" ").map(_.trim).toList

        def getOrQuery(word: String, url: Option[String]): Option[Song] = url match {
            case Some(s) => Option(Song(word, s))
            case None => queryWord(word).headOption
        }

        def processWords(words: List[String], idx: Int, acc: List[(String, Option[Song])]): List[(String, Option[Song])] = {
            if (idx >= words.length) {
                acc
            } else {
                val song: Option[Song] = getOrQuery(words(idx), redisClient.get(words(idx)))
                song match {
                    case Some(s) => redisClient.set(words(idx), s.url)
                    case None =>
                }
                processWords(words, idx + 1, acc ::: List((words(idx), song)))
            }
        }

        processWords(words, 0, Nil)
    }

    def queryWord(word: String): List[Song] = {
        @tailrec
        def scrapeSongList(elements: Elements,
                           idx: Int,
                           acc: List[Song],
                           count: Int,
                           limit: Int): List[Song] = {
            if (idx >= elements.size() || count >= limit) {
                acc
            } else {
                val el = elements.get(idx)
                val title = el.text()
                val url = urlPrefix + el.select("a").attr("href")
                val songObj = Song(title, url)
                scrapeSongList(elements, idx + 1, acc ::: List(songObj), count + 1, limit)
            }
        }

        val doc: Document = getJsoupDoc("https://soundcloud.com/search/sounds?q=" + word)
        // Search for ul's since javascript is disabled in this case.
        val songList = doc.select("ul").get(1).select("li")

        scrapeSongList(songList, 0, Nil, 0, limit)
          .filter(_.title.length >= word.length) // minimum as long as the word itself
          .filter(_.jaccardIndex(word) <= maxJaccard)
          .sortBy(_.title.length) // sort in ascending order (shortest result first)
    }


    def getEmbedUrl(trackUrl: String): String = {
        val doc: Document = getJsoupDoc(trackUrl)
        doc.select("meta[itemprop=\"embedUrl\"]").attr("content")
    }

    def getJsoupDoc(url: String): Document = Jsoup
      .connect(url)
      .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
      .referrer("http://www.google.com")
      .get()

}
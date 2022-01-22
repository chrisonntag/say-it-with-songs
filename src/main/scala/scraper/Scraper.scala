package scraper

import com.redis.RedisClient
import org.jsoup.{Connection, Jsoup}
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import scraper.Proxy

import java.net.ConnectException
import scala.annotation.tailrec
import scala.util.Random


class Scraper(t: String) {

    var urlPrefix: String = "https://soundcloud.com"
    var text: String = t
    var limit: Int = Int.MaxValue
    var maxJaccard: Int = 3
    val redisClient = new RedisClient("localhost", 6379)
    val userAgents = Seq(
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0",
        "Mozilla/5.0 (Linux; Android 11; SM-A426U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.105 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; moto g(50) Build/RRFS31.Q1-59-76-2; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/92.0.4515.159 Mobile Safari/537.36 EdgW/1.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/92.0.4515.90 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:93.0) Gecko/20100101 Firefox/93.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.85 Safari/537.36 Edg/90.0.818.46"
    )

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
        def scrapeSongList(elements: Elements, idx: Int, acc: List[Song], count: Int, limit: Int): List[Song] = {
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

        getJsoupDoc("https://soundcloud.com/search/sounds?q=" + word) match {
            case Some(doc) =>
                // Search for ul's since javascript is disabled in this case.
                val songList = doc.select("ul").get(1).select("li")

                scrapeSongList(songList, 0, Nil, 0, limit)
                  .filter(_.title.length >= word.length) // minimum as long as the word itself
                  .filter(_.jaccardIndex(word) <= maxJaccard)
                  .sortBy(_.title.length) // sort in ascending order (shortest result first)
            case None =>
                println("Could not get tracks document.")
                List(Song(word, ""))
        }
    }

    def getEmbedUrl(trackUrl: String): String = {
        getJsoupDoc(trackUrl) match {
            case Some(doc) => doc.select("meta[itemprop=\"embedUrl\"]").attr("content")
            case None =>
                println("Could not connect to get embedUrl for this track: " + trackUrl)
                ""
        }
    }

    def getJsoupDoc(url: String): Option[Document] = {
        val random = new Random
        val ua = userAgents(random.nextInt(userAgents.length))
        val connection: Connection = Jsoup.connect(url).userAgent(ua).referrer("http://www.google.com")

        def tunnelConnection(conn: Connection, proxy: (String, Int)): Connection = proxy match {
            case (host, port) if port > 0 => conn.proxy(host, port)
            case _ =>
                println("Connecting without proxy")
                conn
        }

        try {
            Some(tunnelConnection(connection, Proxy.getProxy()).get())
        } catch {
            case e: Exception =>
                println(e)
                None
        }
    }

}
package scraper

import com.redis.RedisClient
import org.apache.commons.lang3.{SerializationUtils, StringUtils}
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.jsoup.{Connection, Jsoup}
import com.redis.serialization._
import Parse.Implicits.parseByteArray
import scraper.model.Song

import scala.annotation.tailrec
import scala.util.Random


/**
 * Initiates the scraper.
 */
class Scraper() {

    var urlPrefix: String = "https://soundcloud.com"
    var limit: Int = Int.MaxValue
    var minDistance: Double = 0.8
    val redisClient = new RedisClient("localhost", 6379)
    val userAgents = Seq(
        "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0",
        "Mozilla/5.0 (Linux; Android 11; SM-A426U) AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/89.0.4389.105 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; moto g(50) Build/RRFS31.Q1-59-76-2; wv) " +
          "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/92.0.4515.159 Mobile Safari/537.36 EdgW/1.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
          "CriOS/92.0.4515.90 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
          "Version/13.0 Safari/605.1.15",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
          "Version/14.1.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:93.0) Gecko/20100101 Firefox/93.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/90.0.4430.85 Safari/537.36 Edg/90.0.818.46"
    )

    /**
     * Queries a given text for songs on SoundCloud with similar titles.
     * @param text The text.
     * @return A list of word, song tuples.
     */
    def queryText(text: String): List[(String, Option[Song])] = {
        val words = text.split(" ").map(_.trim).toList

        /**
         * Queries songs for a word if the @serializedSong Option is None.
         * @param word The word to be quried.
         * @param serializedSong Serialized object option.
         * @return An optional Song.
         */
        def getOrQuery(word: String, serializedSong: Option[Array[Byte]]): Option[Song] = serializedSong match {
            case Some(s) => {
                val song: Song = SerializationUtils.deserialize(s).asInstanceOf[Song]
                Option(song)
            }
            case None => queryWord(word).headOption
        }

        /**
         * Recursively iterates over a list of words, checks whether there exists a song in redis,
         * queries SoundCloud and sets a song in redis otherwise.
         * @param words List of words.
         * @param idx Current index in the list.
         * @param acc The remaining list.
         * @return A list of word, song tuples.
         */
        def processWords(words: List[String], idx: Int, acc: List[(String, Option[Song])]): List[(String, Option[Song])] = {
            if (idx >= words.length) {
                acc
            } else {
                val song: Option[Song] = getOrQuery(words(idx), redisClient.get[Array[Byte]](words(idx)))
                song match {
                    case Some(s) => redisClient.set(words(idx), SerializationUtils.serialize(s))
                    case None =>
                }
                processWords(words, idx + 1, acc ::: List((words(idx), song)))
            }
        }

        processWords(words, 0, Nil)
    }

    /**
     * Scrapes the SoundCloud page for a given word and returns a list of songs with titles similar to the word.
     * @param word The word.
     * @return A filtered and sorted list according to some distance metric.
     */
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
                  .filter(_.getDistance(word) >= minDistance)
                  .sortBy(_.title.length) // sort in ascending order (shortest result first)
            case None =>
                println("Could not get tracks document.")
                List(Song(word, ""))
        }
    }

    /**
     * Scrapes SoundCloud's meta headers of track pages for an embed URL.
     * @param trackUrl The track URL.
     * @return The embed URL.
     */
    def getEmbedUrl(trackUrl: String): String = {
        def queryEmbedUrl(): String = {
            getJsoupDoc(trackUrl) match {
                case Some(doc) => {
                    val embedUrl: String = doc.select("meta[itemprop=\"embedUrl\"]").attr("content")
                    redisClient.set(trackUrl, SerializationUtils.serialize(embedUrl))
                    embedUrl
                }
                case None => {
                    println("Could not connect to get embedUrl for this track: " + trackUrl)
                    ""
                }
            }
        }

        redisClient.get[Array[Byte]](trackUrl) match {
            case Some(embedUrl) => SerializationUtils.deserialize(embedUrl)
            case None => queryEmbedUrl()
        }
    }

    /**
     * Returns an option on a JSoup Document.
     * @param url The url of the page.
     * @return
     */
    def getJsoupDoc(url: String): Option[Document] = {
        val random = new Random
        val ua = userAgents(random.nextInt(userAgents.length))
        val connection: Connection = Jsoup.connect(url).userAgent(ua).referrer("http://www.google.com")

        def tunnelConnection(conn: Connection, proxy: (String, Int)): Connection = proxy match {
            case (host, port) if port > 0 =>
                println("Using proxy " + host + ":" + port)
                conn.proxy(host, port)
            case _ =>
                println("Connecting without proxy")
                conn
        }

        try {
            Some(connection.get())
        } catch {
            case e: Exception =>
                println(e)
                None
        }
    }

}

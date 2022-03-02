package scraper

import cask.model.Response
import com.redis.RedisClient
import jdk.internal.platform.Container
import scraper.Scraper
import scraper.model.Song
import upickle.default._


case class Embed(word: String, embedUrl: String, title: String) {}

/**
 * Cask main route object.
 */
object Main extends cask.MainRoutes {
    override def port: Int = 8081
    override def host: String = "0.0.0.0"

    @cask.postJson("/api")
    def getResults(text: String): Response[ujson.Obj] = {
        val scraper: Scraper = new Scraper()
        val res: List[(String, Option[Song])] = scraper.queryText(text)

        def processSongs(pairs: List[(String, Option[Song])], idx: Int, acc: List[Embed]): List[Embed] = {
            if (idx >= pairs.length) {
                acc
            } else {
                val song = pairs(idx)._2.getOrElse(Song("", ""))
                if (song.url.nonEmpty) {
                    val e: Embed = Embed(pairs(idx)._1, scraper.getEmbedUrl(song.url), song.title)
                    processSongs(res, idx + 1, acc ::: List(e))
                } else {
                    val e: Embed = Embed(pairs(idx)._1, song.url, song.title)
                    processSongs(res, idx + 1, acc ::: List(e))
                }
            }
        }
        //processSongs(res, 0, Nil)
        val processed: List[Embed] = processSongs(res, 0, Nil)
        val serialized = processed.map(
            e => {
                ujson.Obj(
                    "word" -> ujson.Str(e.word),
                    "embedUrl" -> ujson.Str(e.embedUrl),
                    "title" -> ujson.Str(e.title)
                )
            }
        )

        cask.Response(ujson.Obj("words" -> serialized), 200,
            Seq(
                ("Access-Control-Allow-Origin", "*"),
                ("Access-Control-Allow-Headers", "*"),
                ("Content-Type", "application/json")
            )
        )
    }

    @cask.route("/api", methods = Seq("options"))
    def cors(request: cask.Request): Response[String] = {
        cask.Response("", 200, Seq(("Access-Control-Allow-Origin", "*"), ("Access-Control-Allow-Headers", "*")))
    }

    initialize()
}

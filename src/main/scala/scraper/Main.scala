package scraper

import jdk.internal.platform.Container
import scraper.Scraper
import upickle.default._


case class Embed(word: String, embedUrl: String) {}

object Main extends cask.MainRoutes {
    override def port: Int = 8081

    @cask.postJson("/api")
    def getResults(text: String) = {
        val scraper: Scraper = new Scraper(text)
        val res: List[(String, Option[Song])] = scraper.queryText(text)

        println(res)

        def processSongs(pairs: List[(String, Option[Song])], idx: Int, acc: List[Embed]): List[Embed] = {
            if (idx >= pairs.length) {
                acc
            } else {
                val song = pairs(idx)._2.getOrElse(Song("", ""))
                if (song.url.nonEmpty) {
                    val e: Embed = Embed(pairs(idx)._1, scraper.getEmbedUrl(song.url))
                    processSongs(res, idx + 1, acc ::: List(e))
                } else {
                    val e: Embed = Embed(pairs(idx)._1, song.url)
                    processSongs(res, idx + 1, acc ::: List(e))
                }
            }
        }
        //processSongs(res, 0, Nil)
        val processed: List[Embed] = processSongs(res, 0, Nil)
        println(processed)
        val serialized = processed.map(
            e => {
                ujson.Obj("word" -> ujson.Str(e.word), "embedUrl" -> ujson.Str(e.embedUrl))
            }
        )

        upickle.default.write(ujson.Obj("data" -> serialized))
    }

    initialize()
}

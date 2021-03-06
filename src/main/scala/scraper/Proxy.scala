package scraper

import org.jsoup.{Connection, Jsoup}
import org.jsoup.nodes.Document

import java.net.ConnectException



object Proxy {
    def testProxy(host: String, port: Int): Boolean = {
        try {
            Jsoup.connect("http://www.google.com").timeout(2 * 1000).proxy(host, port).get()
            true
        } catch {
            case e: ConnectException =>
                println("Proxy connection refused.")
                false
        }
    }

    def getProxy(): (String, Int) = {
        val ipPort = "((?:[0-9]{1,3}\\.){3}(?:[0-9]{1,3}))\\:([0-9]{1,5})".r
        def matchIPPort(content: String): (String, Int) = content match {
            case ipPort(ip, port) => (ip, port.toInt)
            case _ =>
                println("Proxy response can not be parsed: " + content)
                ("", -1)
        }

        val doc: Option[Document] = try {
            //val url = "http://pubproxy.com/api/proxy?limit=1&format=txt&https=true&last_check=1&type=http"
            val url = "https://www.proxyscan.io/api/proxy?format=txt&type=https&limit=1&ping=50"
            Some(Jsoup.connect(url).timeout(2 * 1000).get())
        } catch {
            case e: Exception =>
                println(e)
                None
        }

        doc match {
            case Some(d) =>
                matchIPPort(d.select("body").text()) match {
                    case (host, port) if port > 0 =>
                        println("Testing proxy " + host + ":" + port)
                        if (testProxy(host, port)) {
                            (host, -1)
                        } else {
                            ("", -1)
                        }
                    case _ => ("", -1)
                }
            case None =>
                println("Could not connect to proxy list")
                ("", -1)
        }
    }
}

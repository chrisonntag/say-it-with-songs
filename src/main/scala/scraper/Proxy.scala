package scraper

import org.jsoup.Jsoup


object Proxy {
    def getProxy(): (String, Int) = {
        val ipPort = "((?:[0-9]{1,3}\\.){3}(?:[0-9]{1,3}))\\:([0-9]{1,5})".r
        def matchIPPort(content: String): (String, Int) = content match {
            case ipPort(ip, port) => (ip, port.toInt)
            case _ => ("", -1)
        }

        val doc = Jsoup.connect("http://pubproxy.com/api/proxy?limit=1&format=txt&https=true&referer=true&last_check=1&type=http").get()
        val p = doc.select("body").text()
        matchIPPort(p)
    }
}

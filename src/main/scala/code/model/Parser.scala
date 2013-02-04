package code.model

import java.io.{ InputStream, FileInputStream, File }
import scalax.io.Resource
import scalax.io.Codec
import net.liftweb.http.LiftRules
import net.liftweb.common.Full
import scala.xml.NodeSeq
import scala.xml._
import parsing._
import java.io.FileReader
import net.liftweb.common.Box
import com.amazonaws.services.simpledb._
import com.amazonaws.services.simpledb.model.SelectRequest
import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.simpledb.model.Item
import scala.collection.JavaConversions._
import net.liftweb.common.Empty
import java.net.URL
import com.amazonaws.services.simpledb.model.ReplaceableItem
import com.amazonaws.services.simpledb.model.PutAttributesRequest
import com.amazonaws.services.simpledb.model.ReplaceableAttribute
import com.amazonaws.services.simpledb.model.BatchPutAttributesRequest
import java.io.FileNotFoundException

case class Entry(page: String, links: Set[String], words: Set[String])

trait Crawler {

  def exprToLinksDomain: String
  def linkToPageRankDomain: String

  def load(source: InputStream): Node = {
    HMTL5Parser.loadXML(new InputSource(source))
  }
  def loadPage(name: String): Box[NodeSeq]

  def isHTML(f: File): Boolean = f.getName.endsWith(".html")

  def webPath(prefix: String, link: String): String = {
    link match {
      case x if x.startsWith("http://") || x.startsWith("https://") => x
      case _ => prefix + "/" + link
    }
  }

  private def anchorTags(ns: NodeSeq) = ns \\ "a"

  private def href(ns: NodeSeq): List[String] = {
    anchorTags(ns).map(_ \ "@href").toList.map(_.toString).filter(_ != "").filter(!_.startsWith("mailto:"))
  }

  def crawlPage(start: String, ns: NodeSeq): Set[String] = href(ns).map(webPath(start, _).replace("//", "/").replace(":", ":/")).toSet
  def parsePage(ns: NodeSeq): Set[String] = {
    val text = ns.mkString.replaceAll("<[^>]+>", " ").mkString
    (for (word <- text.split("""[^A-Z^a-z]""") if word != "")
      yield word.toLowerCase).toSet
  }
  def crawl(start: String, limit: String): Set[Entry]

  def storeLinks(page: String, links: Set[String]): Unit
  def storeExpr(words: Set[String], page: String): Unit
  def store(entries: Set[Entry]): Unit = {
    entries.map { entry =>
      storeLinks(entry.page, entry.links)
      storeExpr(entry.words, entry.page)
    }
  }
}

trait Searcher {
  def search(expr: String): Set[String]
  def order(links: Set[String]): List[String]
}

object HMTL5Parser extends NoBindingFactoryAdapter {
  override def loadXML(source: InputSource, _p: SAXParser) = {
    loadXML(source)
  }

  def loadXML(source: InputSource) = {
    import nu.validator.htmlparser.{ sax, common }
    import sax.HtmlParser
    import common.XmlViolationPolicy

    val reader = new HtmlParser
    reader.setXmlPolicy(XmlViolationPolicy.ALLOW)
    reader.setContentHandler(this)
    reader.parse(source)
    rootElem
  }
}

object Amazon extends Searcher with Crawler {
  def select(domain: String) = "select * from `" + domain + "`"

  def exprToLinksDomain = "WebAppSimpleDB"
  def linkToPageRankDomain = "PageRankSimpleDB"

  def linksQuery(expr: String) = select(exprToLinksDomain) + expr
  def rankQuery(expr: String) = select(linkToPageRankDomain) + expr + " and PageRank > '0' order by PageRank desc"

  def connection: Box[AmazonSimpleDBClient] =
    for (a <- LiftRules.getResource("/toserve/AwsCredentials.properties")) yield {
      new AmazonSimpleDBClient(new PropertiesCredentials(a.openConnection.getInputStream))
    }

  def query(expr: String): List[Item] = {
    connection match {
      case Full(c) => c.select(new SelectRequest(expr)).getItems.toList
      case _ =>
        println("ERROR: Couldn't Query!")
        List.empty
    }
  }

  def put(domain: String, items: List[ReplaceableItem]): Unit = {
    connection match {
      case Full(c) => c.batchPutAttributes(new BatchPutAttributesRequest(domain, items))
      case _ =>
        println("ERROR: Couldn't Insert!")
        Unit
    }
  }

  def whereExpr(itemNames: Set[String]): Box[String] = {
    def wrap(input: List[String]): List[String] = input match {
      case "" :: Nil => Nil
      case x => x.map("itemName() = '" + _ + "'")
    }
    wrap(itemNames.toList) match {
      case Nil => Empty
      case x => Full(" where " + "( " + x.mkString(" or ") + " )")
    }
  }

  def loadPage(name: String): Box[NodeSeq] =
    try {
      Full(load(new URL(name).openConnection.getInputStream))
    } catch {
      case _ : Throwable => Empty
    }

  def crawl(start: String, limit: String): Set[Entry] = {
    def crawlAux(pages: List[String], acc: Int, output: List[Entry]): Set[Entry] = {
      (pages, acc) match {
        // end of the web page list - not likely to happen
        case (Nil, _) => output.toSet
        // max number of pages to process has been reached
        case (_, _) if acc == limit.toInt => output.toSet
        case (x :: xs, _) =>
          // exclude already crawled links
          output.filter(entry => { entry.page == x }).size match {
            case 0 =>
              loadPage(x) match {
                case Full(xml) =>
                  val links = crawlPage(start, xml)
                  val words = parsePage(xml)
                  crawlAux(xs.:::(links.toList).distinct, acc + 1, output.::(new Entry(x, links, words)))
                case _ => crawlAux(xs, acc, output)
              }
            case _ => crawlAux(xs, acc, output)
          }
      }
    }
    (start, limit) match {
      case ("", _) | (_, "") => Set.empty
      case _ => crawlAux(start :: Nil, 0, Nil)
    }
  }

  def storeLinks(page: String, links: Set[String]): Unit = {
    val linksToPageRank: List[List[ReplaceableItem]] = (links.toList.::(page).distinct).map {
      new ReplaceableItem(_, links.map(link => new ReplaceableAttribute(link, link, true)).toList.::(new ReplaceableAttribute("PageRank", "1", true)))
    }.toList.grouped(25).toList
    linksToPageRank.map(put(linkToPageRankDomain, _))
  }
  def storeExpr(words: Set[String], page: String): Unit = {
    val exprToLinks: List[List[ReplaceableItem]] = words.map {
      new ReplaceableItem(_, new ReplaceableAttribute(page, page, true) :: Nil)
    }.toList.grouped(25).toList
    exprToLinks.map(put(exprToLinksDomain, _))
  }

  def search(expr: String): Set[String] = {
    whereExpr(expr.split(" ").toSet) match {
      case Full(expr) => query(linksQuery(expr)).map {
        _.getAttributes.map(_.getValue)
      }.flatten.toSet
      case _ => Set.empty
    }
  }

  def order(links: Set[String]): List[String] = {
    whereExpr(links) match {
      case Full(expr) => query(rankQuery(expr)).map(_.getName)
      case _ => List.empty
    }
  }
}

object FS extends Searcher with Crawler {

  def exprToLinksDomain = "/toserve/out/exprs.txt"
  def linkToPageRankDomain = "/toserve/out/pageRank.txt"

  def loadPage(name: String): Box[NodeSeq] = {
    for (f <- LiftRules.getResource(name)) yield load(f.openConnection.getInputStream)
  }

  def crawl(start: String, limit: String): Set[Entry] = {
    (start, limit) match {
      case ("", _) | (_, "") => Set.empty
      case _ => loadPage(start).map { page =>
        val links = crawlPage(start, page)
        val words = parsePage(page)
        new Entry(start, links, words)
      }.toSet
    }
  }

  def storeLinks(page: String, links: Set[String]): Unit = {
    for {
      d <- LiftRules.getResource(linkToPageRankDomain)
    } yield {
      val pageRank: List[String] =
        Resource.fromFile(d.getPath).reader(Codec.UTF8).lines().toList.filter {
          _ != "\n"
        } ::: (page + " " + "1.0" + " " + links.mkString(" ") + "\n" :: Nil)
      Resource.fromFile(d.getPath).writeStrings(pageRank)(Codec.UTF8)
    }
  }
  def storeExpr(words: Set[String], page: String): Unit = {
    for {
      d <- LiftRules.getResource(exprToLinksDomain)
    } yield {
      val exprs: List[String] =
        Resource.fromFile(d.getPath).reader(Codec.UTF8).lines().toList.filter {
          _ != "\n"
        } ::: words.map {
          word => "\n" + word + " " + page + "\n"
        }.toList
      Resource.fromFile(d.getPath).writeStrings(exprs)(Codec.UTF8)
    }
  }

  def search(expr: String): Set[String] = {
    LiftRules.getResource(exprToLinksDomain) match {
      case Full(d) =>
        Resource.fromFile(d.getPath).reader(Codec.UTF8).lines().toList.filter {
          l =>
            println("LLL " + l)
            l.contains(expr)
        }.map(_.split(" ").tail.toList).toSet.flatten
      case _ => Set.empty
    }
  }

  def order(links: Set[String]): List[String] = {
    LiftRules.getResource(linkToPageRankDomain) match {
      case Full(d) =>
        Resource.fromFile(d.getPath).reader(Codec.UTF8).lines().map(_.split(" ").head).filter {
          l =>
            println("LLL2 " + l)
            links.contains(l)
        }.toList
      case _ => List.empty
    }
  }
}

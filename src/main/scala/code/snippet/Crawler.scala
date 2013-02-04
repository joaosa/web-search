package code.snippet

import scala.xml.NodeSeq
import net.liftweb.util
import util.Helpers.{
  strToSuperArrowAssoc,
  strToCssBindPromoter
}
import code.model.{ FS, Amazon }
import reactive.web.html.TextInput
import reactive.Signal
import reactive.Observing
import reactive.web.html.Button
import net.liftweb.common.Full
import org.joda.time._
import net.liftweb.http.RequestVar
import net.liftweb.common.{ Empty, Box }
import code.model.Entry

class Crawler extends Observing {

  object crawlStartTime extends RequestVar[DateTime](new DateTime)
  object crawlEndTime extends RequestVar[DateTime](new DateTime)

  object data extends RequestVar[Set[Entry]](Set.empty)

  object storeStartTime extends RequestVar[DateTime](new DateTime)
  object storeEndTime extends RequestVar[DateTime](new DateTime)

  val crawl = Button("Crawl") {
    crawlStartTime.set(new DateTime)
    data.set(Amazon.crawl(start.value.value, limit.value.value))
    crawlEndTime.set(new DateTime)
  }
  val store = Button("Store") {
    storeStartTime.set(new DateTime)
    Amazon.store(data.is)
    storeEndTime.set(new DateTime)
  }

  val start = TextInput()
  start.value.updateOn(crawl.click, store.click)

  val limit = TextInput()
  limit.value.updateOn(crawl.click, store.click)

  val crawlSignal: Signal[NodeSeq => NodeSeq] = start.value map {
    v =>
      { _: NodeSeq =>
        <b>Crawler took: { new Period(crawlStartTime.is, crawlEndTime.is).getMillis } miliseconds.</b>
        <ul>
          {
            data.is.map {
              entry => <li>{ entry }</li>
            }
          }
        </ul>: NodeSeq
      }
  }
  val crawlOuput = reactive.web.Cell(crawlSignal)

  val storeSignal: Signal[NodeSeq => NodeSeq] = start.value map {
    v =>
      { _: NodeSeq =>
        <b>Store took: { new Period(storeStartTime.is, storeEndTime.is).getMillis } miliseconds.</b>
      }
  }
  val storeOutput = reactive.web.Cell(storeSignal)

  def render = {
    "#crawlerInput" #> start &
      "#crawlerLimit" #> limit &
      "#crawlerTrigger" #> crawl &
      "#crawlerOutput" #> crawlOuput &
      "#storeTrigger" #> store &
      "#storeOutput" #> storeOutput
  }
}

package code.snippet

import scala.xml.NodeSeq
import net.liftweb.util
import util.Helpers.{
  strToSuperArrowAssoc,
  strToCssBindPromoter
}
import reactive.web.html.TextInput
import reactive.web.PropertyVar
import reactive.{ Signal, Observing }
import reactive.web.RElem.rElemToNsFunc
import code.model.{ FS, Amazon }
import reactive.web.html.Button
import net.liftweb.http.RequestVar
import org.joda.time._

class Search extends Observing {

  object searchStartTime extends RequestVar[DateTime](new DateTime)

  val searchTrigger = Button("Search") {}

  val search = TextInput()
  search.value updateOn searchTrigger.click

  val triggerSignal: Signal[NodeSeq => NodeSeq] = search.value map {
    v =>
      { _: NodeSeq =>
        { searchStartTime.set(new DateTime) }
        <ul>
          {
            Amazon.order(Amazon.search(v)).map { entry =>
              <li><a href={ entry }>{ entry }</a></li>
            }
          }
        </ul>
        <b>Search took: { new Period(searchStartTime.is, new DateTime).getMillis } miliseconds.</b>: NodeSeq
      }
  }
  val display = reactive.web.Cell(triggerSignal)

  def render = {
    "#searchInput" #> search &
      "#searchTrigger" #> searchTrigger &
      "#searchOutput" #> display
  }
}

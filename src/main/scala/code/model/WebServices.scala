package code.model
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.JsonAST.JValue
import net.liftweb.common.Box
import net.liftweb.common.Empty
import net.liftweb.json.JsonAST.JString
import net.liftweb.common.Full
import net.liftweb.http.S
import net.liftweb.http.LiftResponse
import net.liftweb.http.OkResponse
import org.joda.time.DateTime
import org.joda.time.Period
import net.liftweb.http.JsonResponse

object WebServices extends RestHelper {
  serve {
    case "crawl" :: limit :: Nil Get _ =>
      val start = new DateTime
      Amazon.crawl("http://www.ist.utl.pt", limit)
      Full(JsonResponse(new Period(start, new DateTime).getMillis))
    /*case "store" :: Nil Post  _ =>
        Amazon.crawl("http://wwww.ist.utl.pt", limit)*/
    case "search" :: expr :: Nil Get _ =>
      val start = new DateTime
      Amazon.search(expr)
      Full(JsonResponse(new Period(start, new DateTime).getMillis))
  }
}

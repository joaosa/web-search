package bootstrap.liftweb

import scala.language.postfixOps
import net.liftweb._
import util._
import Helpers._
import common._
import http._
import sitemap._
import Loc._
import mapper._
import mapper.view.TableEditor
import net.liftweb.http.js.jquery.JQueryArtifacts
import net.liftmodules.widgets.flot._
import reactive.web.Reactions
import net.liftweb.http.ResourceServer
import code.model.WebServices

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {
    if (!DB.jndiJdbcConnAvailable_?) {
      val vendor =
        new StandardDBVendor(Props.get("db.driver") openOr "org.h2.Driver",
          Props.get("db.url") openOr
            "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
          Props.get("db.user"), Props.get("db.password"))

      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }

    // where to search snippet
    LiftRules.addToPackages("code")

    // Build SiteMap
    def sitemap() = SiteMap(
      Menu(S ? "Search") / "index",
      Menu(S ? "Crawler") / "crawler")

    //def sitemapMutators = User.sitemapMutator
    // set the sitemap.  Note if you don't want access control for
    // each page, just comment this line out.
    //LiftRules.setSiteMapFunc(() => sitemapMutators(sitemap))
    LiftRules.setSiteMapFunc(() => sitemap())

    // Use jQuery
    LiftRules.jsArtifacts = JQueryArtifacts

    //Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    // Make notices fade out
    LiftRules.noticesAutoFadeOut.default.set((noticeType: NoticeType.Value) =>
      Full((1 seconds, 2 seconds)))

    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    // What is the function to test if a user is logged in?
    //LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))

    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)

    // Start reactive web
    Reactions.init
    
    // Allow access to local pages
    ResourceServer.allow({
      case "in" :: _ => true
    })
    
    // WEB services
    LiftRules.statelessDispatch.append(WebServices)
  }
}

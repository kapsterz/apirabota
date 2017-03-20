package actors

import java.io._
import javax.inject._

import actors.FileParserActor.ResultFileRow
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import play.api.libs.json.{JsObject, JsValue, Json, OFormat}
import play.api.libs.ws.WSClient

import scala.collection.immutable.IndexedSeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try


object FileParserActor {

  case class ResultFileRow(url: String, xPath: String) {
    def toJson = {
      Json.obj(
        "url" -> url,
        "id" -> xPath
      )
    }
  }

}

/**
  * Created by wegod on 10.03.2017.
  */
class FileParserActor @Inject()(implicit exc: ExecutionContext, val materializer: Materializer, ws: WSClient)
//  extends Actor
{
  implicit val Logger = play.api.Logger("custom")


  //  override def receive: Receive = {
  //    case GetFileToParse(file) => parseFile(file)
  //    case x => Logger.info("Receive unidentified massage: " + x)
  //  }
  def parseJson(file: Seq[ResultFileRow]): Future[JsObject] = {
    Logger.info("Success recieved message for Json. Operation start.")
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"
    val url = file.head.url
    def recover: Future[Seq[Seq[String]]] = {
      val response = ws.url(url)
        .withHeaders("User-Agent" -> userAgent)
        .withFollowRedirects(true)
        .withRequestTimeout(Duration(30, SECONDS))
        .get
      response.map { resp =>
        file.map { xPath =>
          val body: Element = Jsoup.parse(resp.body).body
          body.select("script").remove()
          var elements = Seq.empty[String]
          for {
            _ <- 1 to body.select(xPath.xPath).size()
          } yield {
            Try {
              elements = elements :+ body.select(xPath.xPath).first().toString
              body.select(xPath.xPath).first().remove()
            }.toOption
          }
          elements
        }
      }
    }

    recover.recoverWith {
      case ex =>
        println("Exception in first step: " + ex)
        recover
    }
      .map { result =>
        Logger.info("Success processed Json. Response done!")
        Json.obj("result" -> result)
      }
  }


}

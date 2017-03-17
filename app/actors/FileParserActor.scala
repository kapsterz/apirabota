package actors

import java.io._
import javax.inject._

import actors.FileParserActor.{GetFileToParse, ResultFileRow}
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.collection.immutable.IndexedSeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try


object FileParserActor {

  case class GetFileToParse(file: File)

  case class ResultFileRow(info: String, id: String, photoUrl: String, schedules: Option[String], title: String, updated: String,
                           education: Seq[String], experience: Seq[Map[String, String]], language: Seq[String], skills: Seq[String]) {
    def toJson = {
      val scheduless = schedules.getOrElse("0")
      Json.obj(
        "info" -> Json.toJson(info),
        "id" -> id,
        "photoUrl" -> photoUrl,
        "schedules" -> scheduless,
        "title" -> title,
        "updated" -> updated,
        "education" -> Json.toJson(education),
        "experience" -> Json.toJson(experience),
        "language" -> Json.toJson(language),
        "skills" -> Json.toJson(skills)
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
  def parseFile(file: File): Unit = {
    val pw = new PrintWriter(new File("hello.txt"))
    Logger.info("Success recieved message for Process File. Operation start.")
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"
    val scalaSource = scala.io.Source.fromFile(file)
    val sourceFileWithUrl = Source.fromIterator(() => scalaSource.getLines())
    var count = 0
    sourceFileWithUrl.mapAsync(10) { url =>
      def recover: Future[IndexedSeq[String]] = {
        Logger.info("Going in to URL " + url)
        val response = ws.url(url)
          .withHeaders("User-Agent" -> userAgent)
          .withFollowRedirects(true)
          .withRequestTimeout(Duration(30, SECONDS))
          .get
        response.map { resp =>
          val body: Document = Jsoup.parse(resp.body)
          val maxInt = 100
          //              Try(body.select("div.media.pager > div.media-body > span > span > a.pager-link").last().text().toInt).toOption.getOrElse(59)
          val r = for {
            i <- 1 to maxInt
          } yield {
            url + s"&pg=$i"
          }
          //            println(r)
          r
        }
      }

      recover.recoverWith {
        case ex =>
          println("Exception in zero step: " + ex)
          recover
      }
    }.mapConcat(identity)
      .mapAsync(10) { url =>
        def recover: Future[IndexedSeq[String]] = {
          val response = ws.url(url)
            .withHeaders("User-Agent" -> userAgent)
            .withFollowRedirects(true)
            .withRequestTimeout(Duration(30, SECONDS))
            .get
          response.map { resp =>
            val body: Document = Jsoup.parse(resp.body)
            val urls = for {
              i <- 1 to 20
            } yield {
              Try(body.body.select(".rua-p-t_18 a").get(i - 1).attr("href")).toOption
            }
            count = count + urls.flatten.size
            //              println(count)
            urls.flatten.map { url =>
              //                println("http://rabota.ua" + url)
              "http://rabota.ua" + url
            }

          }
        }

        recover.recoverWith {
          case ex =>
            println("Exception in first step: " + ex)
            recover
        }
      }
      .mapConcat(identity)
      .mapAsync(10) { url =>
        def recover: Future[ResultFileRow] = {
          val response = ws.url(url)
            .withHeaders("User-Agent" -> userAgent)
            .withFollowRedirects(true)
            .withRequestTimeout(Duration(30, SECONDS))
            .get
          response.map {
            case resp if resp.status == 200 =>
              //                println("This in with url:" + url)
              val body: Document = Jsoup.parse(resp.body)
              val info = Try(body.select("#PersonalDataHolder > div:eq(2) > div:eq(0) > div:eq(1) > div > div > p").first().text()).toOption.getOrElse("-")
              val id = Try(url.split("/cv/")(1)).toOption.getOrElse("Nema(")
              val photo = Try(body.select("#centerZone_BriefResume1_CvView1_cvHeader_imageUser").first().attr("src")).toOption.getOrElse("Nema(")
              //                println(photo)
              val schedules = Try(body.select("#PersonalDataHolder > div)").get(5).text()).toOption
              val title = Try(body.select("#centerZone_BriefResume1_CvView1_cvHeader_txtJobName").first().text()).toOption.getOrElse("Nema(")
              val updated = Try(body.select("#PersonalDataHolder > div:eq(1) > div > div:eq(1) > span").first().text()).toOption.getOrElse("Nema(")
              val education = Try(body.select("#EducationHolder").first().text()).toOption.getOrElse("Nema(")
              val experience = {
                val expBody = {
                  if (body.select("#ExperienceHolder *").size() > 2) {
                    body.select("#ExperienceHolder *").first().remove()
                    body.select("#ExperienceHolder *").first().remove()
                    body.select("#ExperienceHolder *")
                  } else {
                    body.select("#ExperienceHolder *")
                  }
                }

                def expFinder(expBody: Elements, expResult: Seq[Map[String, String]]): Seq[Map[String, String]] = {
                  if (expBody.select("p.muted").isEmpty) {
                    expResult
                  } else {
                    def expExtractor(newExpBody: Elements, expResults: Map[String, String], int: Int): (Map[String, String], Elements) = {
                      if ((newExpBody.size == 0) && expBody.get(int).hasClass(".muted")) {
                        newExpBody.first().remove()
                        expResults -> newExpBody
                      } else {
                        println(newExpBody.first())
                        newExpBody.first().remove()
                        expExtractor(newExpBody, expResults + (int.toString -> expBody.get(int - 1).text()), int + 1)
                      }
                    }
                    val res = expBody
                    val t = {
                      if (res.size() > 1)
                        expExtractor(res, Map.empty[String, String], 2)
                      else expExtractor(res, Map.empty[String, String], 1)
                    }
                    expFinder(t._2, expResult :+ (t._1 + ("0" -> expBody.get(0).text())))
                  }
                }

                expFinder(expBody, Seq.empty[Map[String, String]])
              }
              println(experience)
              val language = {
                val lang = for {
                  i <- 1 to 5
                } yield {
                  Try(body.select("#LanguagesHolder p").get(i).text()).toOption
                }
                lang.flatten
              }
              val skills = Try(body.select("#SkillsHolder").first().text()).toOption.getOrElse("Nema(")
              pw.write(ResultFileRow(
                info,
                id,
                photo,
                schedules,
                title,
                updated,
                Seq(education),
                experience,
                language,
                Seq(skills)
              ).toJson + "\n")
              ResultFileRow(
                info,
                id,
                photo,
                schedules,
                title,
                updated,
                Seq(education),
                experience,
                language,
                Seq(skills)
              )
            case resp =>
              println("Get respons status:" + resp.status)
              throw new Exception
          }
        }

        recover.recoverWith {
          case ex =>
            ex.printStackTrace()
            recover
        }
      }.runFold(Seq.empty[ResultFileRow]) { (i, j) =>
      i :+ j
    }.onSuccess { case result =>
      Logger.info("Success processed file. Start write in file hello.txt")
      pw.close()
      Logger.info("Success wrote in  file hello.txt")
    }
  }


}

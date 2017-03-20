package controllers

import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api._
import play.api.mvc._
import javax.inject._
import actors.FileParserActor.ResultFileRow
import actors.FileParserActor
import play.api.data.Forms._
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.libs.json.JsValue

import scala.concurrent.ExecutionContext


class Application @Inject()(fileParserActor: FileParserActor)(implicit executionContext: ExecutionContext) extends Controller {
  private val userForm = Form(
    mapping(
      "requestUrl" -> text,
      "requestXPath" -> text
    )(ResultFileRow.apply)(ResultFileRow.unapply)
  )
  def main = Action{
    Ok(views.html.main(userForm))
  }
  def index: Action[ResultFileRow] = Action.async(parse.form(userForm)) {
    implicit request =>
      Logger.info("Success received message for process json")
      val result = fileParserActor.parseJson(request.body)
      result.map { f =>
        Ok(f)
      }
  }

}
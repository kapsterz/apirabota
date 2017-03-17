package controllers

import java.io.File

import play.api._
import play.api.mvc._
import javax.inject._

import actors.FileParserActor
import actors.FileParserActor.GetFileToParse
import akka.actor._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext


class Application @Inject()(fileParserActor: FileParserActor)(implicit executionContext: ExecutionContext) extends Controller {
  def index = Action.async {
    Logger.info("success send message for process file " + new File("new.txt"))
    val result = fileParserActor.parseFile(new File("new.txt"))
    result.map{ f=>
      Ok(Json.obj("all" -> f))
    }
  }

}
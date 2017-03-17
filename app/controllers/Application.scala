package controllers

import java.io.File

import play.api._
import play.api.mvc._
import javax.inject._

import actors.FileParserActor
import actors.FileParserActor.GetFileToParse
import akka.actor._


class Application @Inject()(fileParserActor: FileParserActor) extends Controller {
  def index = Action {
    Logger.info("success send message for process file " + new File("new.txt"))
    fileParserActor.parseFile(new File("new.txt"))
    Ok("success")
  }

}
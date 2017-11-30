package com.mchange.sc.v2.jsonrpc

import scala.collection._
import scala.concurrent.{blocking,ExecutionContext,Future}
import scala.util.{Try,Success,Failure}
import scala.io.Codec

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets.UTF_8

import com.mchange.sc.v2.json

import play.api.libs.json._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v2.playjson.BufferedJsValueSource

import com.mchange.sc.v1.log.MLevel._

object Exchanger {
  private implicit lazy val logger = mlogger( this )

  final object Factory {

    def createSimpleFactory() : Exchanger.Factory       = Simple
    def createAsyncFactory()  : Exchanger.Factory.Async = new jetty.JettyExchanger.Factory()

    implicit lazy val Default : Exchanger.Factory.Async = new jetty.JettyExchanger.Factory( jetty.JettyExchanger.Factory.defaultInstanceBuildClient _ )

    final object Simple extends Exchanger.Factory {
      def apply( url : URL ) : Exchanger = new Exchanger.Simple( url )
      def close() : Unit = () //nothing to do
    }
    trait Async extends Factory // just a marker trait
  }
  trait Factory extends AutoCloseable {
    def apply( url : URL ) : Exchanger
    def close() : Unit

    def apply( url : String ) : Exchanger = this.apply( new URL( url ) )
  }
  final class Simple( httpUrl : URL ) extends Exchanger {
    TRACE.log( s"${this} created, using URL '$httpUrl'" )

    def exchange( methodName : String, paramsArray : JsArray )( implicit ec : ExecutionContext ) : Future[Response] = Future {

      val id = newRandomId()

      val paramsBytes = traceRequestBytes( id, methodName, paramsArray )

      val htconn = httpUrl.openConnection().asInstanceOf[HttpURLConnection]
      htconn.setDoOutput( true )
      htconn.setInstanceFollowRedirects( false )
      htconn.setUseCaches( false )
      htconn.setRequestMethod( "POST" );
      htconn.setRequestProperty( "Content-Type", "application/json")
      htconn.setRequestProperty( "charset", "utf-8" )
      htconn.setRequestProperty( "Content-Length", paramsBytes.length.toString )

      blocking {
        borrow( htconn.getOutputStream() )( _.write(paramsBytes) )
        borrow( htconn.getInputStream() )( traceParseResponse[InputStream]( id, _ ) )
      }
    }
    def close() : Unit = ()
  }
}
trait Exchanger extends AutoCloseable {
  import Exchanger.logger

  protected def newRandomId() = scala.util.Random.nextInt()

  protected def traceRequestBytes( id : Int, methodName : String, paramsArray : JsArray ) : Array[Byte] = {
    val paramsJsObj = JsObject( Seq( "jsonrpc" -> JsString("2.0"), "method" -> JsString(methodName), "params" ->  paramsArray, "id" -> JsNumber(id) ) )
    val jsonString = Json.asciiStringify( paramsJsObj )
    TRACE.log("Generated JSON Request: " + jsonString)
    jsonString.getBytes( UTF_8 )
  }

  protected def traceParseResponse[T : BufferedJsValueSource]( id : Int, src : T ) : Response = {
    traceAttemptParseResponse( id, src ).get
  }

  // sometimes network stacks corrupt with NUL characters, illegal in JSON
  // so we try to filter for those if something goes wrong.
  protected def traceAttemptParseResponse[T : BufferedJsValueSource]( id : Int, src : T ) : Try[Response] = {
    val buffered = implicitly[BufferedJsValueSource[T]].toBufferedJsValue( src )

    def firstTry  = Try( buffered.toJsValue )
    def secondTry = Try {
      val out = buffered.transform( json.removeNulTermination ).toJsValue
      DEBUG.log("Had to remove unexpected NUL termination from JSON" )
      out
    }
    def thirdTry  = Try( buffered.transform( filterControlCharacters ).toJsValue )
    val allTries = (firstTry orElse secondTry orElse thirdTry)

    // we're just logging the parsed value here on success as we return it.
    // on failure, we log the (third-try) JSON that failed to parse
    allTries.map( jsv => TRACE.logEval("Parsed JSON response: ")(jsv) ).map( _.as[Response] ensuring goodId( id ) ) match {
      case ok   @ Success(_) => ok
      case oops @ Failure(_) => {
        WARNING.log( s"Response parsing failed. Content (interpreted as UTF8):\n${new String(buffered.buffer.toArray, Codec.UTF8.charSet)}" )
        oops
      }
    }
  }

  protected def goodId( id : Int ) : PartialFunction[Response, Boolean] = {
    case Yang( success ) => success.id == id
    case Yin( error )    => error.id.fold( true )( _ == id )
  }

  def exchange( methodName : String, paramsArray : JsArray )( implicit ec : ExecutionContext ) : Future[Response]

  def close() : Unit

  private def filterControlCharacters( input : immutable.Seq[Byte] ) : immutable.Seq[Byte] = {
    val segregated = json.segregateControlCharacters( input )
    if ( segregated.controlCharacters.nonEmpty ) {
      DEBUG.log( s"""Control characters were removed [${segregated.escapedControlCharacters}] to generate (hopefully) valid JSON "${segregated.clean}"""" )
    }
    segregated.cleanBytes()
  }
}

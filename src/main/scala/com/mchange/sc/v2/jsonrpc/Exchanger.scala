package com.mchange.sc.v2.jsonrpc

import scala.collection._
import scala.concurrent.{blocking,ExecutionContext,Future}

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets.UTF_8

import play.api.libs.json._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v2.playjson.JsValueSource

import com.mchange.sc.v1.log.MLevel._

object Exchanger {
  private implicit lazy val logger = mlogger( this )

  final object Factory {

    def createSimpleFactory() : Exchanger.Factory       = Simple
    def createAsyncFactory()  : Exchanger.Factory.Async = new jetty.JettyExchanger.Factory

    implicit lazy val Default : Exchanger.Factory.Async = createAsyncFactory()

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

      val paramsBytes = requestBytes( id, methodName, paramsArray )

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
        borrow( htconn.getInputStream() )( traceParse[InputStream] ).as[Response] ensuring goodId( id )
      }
    }
    def close() : Unit = ()
  }
}
trait Exchanger extends AutoCloseable {
  import Exchanger.logger

  protected def newRandomId() = scala.util.Random.nextInt()

  protected def requestBytes( id : Int, methodName : String, paramsArray : JsArray ) : Array[Byte] = {
    val paramsJsObj = JsObject( Seq( "jsonrpc" -> JsString("2.0"), "method" -> JsString(methodName), "params" ->  paramsArray, "id" -> JsNumber(id) ) )
    Json.asciiStringify( paramsJsObj ).getBytes( UTF_8 )
  }

  protected def traceParse[T : JsValueSource]( src : T ) : JsValue = {
    TRACE.logEval( "Raw parsed JSON: " )( implicitly[JsValueSource[T]].toJsValue( src ) )
  }

  protected def goodId( id : Int ) : PartialFunction[Response, Boolean] = {
    case Yang( success ) => success.id == id
    case Yin( error )    => error.id.fold( true )( _ == id )
  }

  def exchange( methodName : String, paramsArray : JsArray )( implicit ec : ExecutionContext ) : Future[Response]

  def close() : Unit
}

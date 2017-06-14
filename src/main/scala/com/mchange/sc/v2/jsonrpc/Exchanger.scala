package com.mchange.sc.v2.jsonrpc

import scala.collection._
import scala.concurrent.{blocking,ExecutionContext,Future}

import java.io.{Closeable,InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets.UTF_8

import play.api.libs.json._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v1.log.MLevel._

object Exchanger {
  private implicit lazy val logger = mlogger( this )

  final object Factory {
    final class Simple()( implicit ec : ExecutionContext = ExecutionContext.global ) extends Exchanger.Factory {
      def apply( url : URL ) : Exchanger = new Exchanger.Simple( url )
      def close() : Unit = () //nothing to do
    }
  }
  trait Factory extends Closeable {
    def apply( url : URL ) : Exchanger
    def close() : Unit

    def apply( url : String ) : Exchanger = this.apply( new URL( url ) )
  }
  object Simple {
    // Note: This one-step is safe only because the Simple's factory doesn't need to be closed
    //       Anonoyingly, only one of these is permitted by the compiler to accept the default ExecutionContext argument
    def apply( url : URL )( implicit ec : ExecutionContext ) : Exchanger = (new Factory.Simple()( ec )).apply( url )
    def apply( url : String )( implicit ec : ExecutionContext = ExecutionContext.global ) : Exchanger = this.apply( new URL( url ) )( ec )
  }
  final class Simple( httpUrl : URL )( implicit ec : ExecutionContext ) extends Exchanger {
    TRACE.log( s"${this} created, using URL '$httpUrl'" )

    def exchange( methodName : String, paramsArray : JsArray ) : Future[Response] = Future {

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
        borrow( htconn.getInputStream() )( traceParse ).as[Response] ensuring goodId( id )
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

  protected def traceParse( is : InputStream ) : JsValue = {
    TRACE.logEval( "Raw parsed JSON: " )( Json.parse( is ) )
  }

  protected def traceParse( bytes : Array[Byte] ) : JsValue = {
    TRACE.logEval( "Raw parsed JSON: " )( Json.parse( bytes ) )
  }

  protected def goodId( id : Int ) : PartialFunction[Response, Boolean] = {
    case Yang( success ) => success.id == id
    case Yin( error )    => error.id.fold( true )( _ == id )
  }

  def exchange( methodName : String, paramsArray : JsArray ) : Future[Response]

  def close() : Unit
}

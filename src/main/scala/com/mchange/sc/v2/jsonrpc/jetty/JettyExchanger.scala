package com.mchange.sc.v2.jsonrpc.jetty

import com.mchange.sc.v2.jsonrpc._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v1.log.MLevel._

import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.{Request => JRequest, Response => JResponse, Result => JResult}
import org.eclipse.jetty.client.util.ByteBufferContentProvider

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.ByteBuffer

import scala.concurrent.{Future, Promise}
import scala.util.Try

import play.api.libs.json._

object JettyExchanger {
  private implicit lazy val logger = mlogger( this )

  final class Factory extends Exchanger.Factory {
    val httpClient = new HttpClient()
    httpClient.setFollowRedirects(false)
    httpClient.start()

    def apply( url : URL ) : Exchanger = new JettyExchanger( url, this )

    def close() : Unit = {
      httpClient.stop()
    }
  }
}
class JettyExchanger( url : URL, factory : JettyExchanger.Factory ) extends Exchanger {
  def exchange( methodName : String, paramsArray : JsArray ) : Future[Response] = {
    val id = newRandomId()

    val paramsBytes = requestBytes( id, methodName, paramsArray )

    val byteBuffer = ByteBuffer.wrap( paramsBytes )

    val contentProvider = new ByteBufferContentProvider( "application/json", byteBuffer )

    val request = factory.httpClient.POST( url.toURI ).header( "charset", "utf-8" ).content( contentProvider )

    val promise = Promise[Response]()

    val handler = new JResponse.ContentListener with JResponse.FailureListener with JResponse.CompleteListener {
      def onContent( response : JResponse, content : ByteBuffer ) : Unit = {
        val attempt = Try {
          borrow( new ByteArrayInputStream( content.array ) ) { is =>
            traceParse( is ).as[Response] ensuring goodId( id )
          }
        }
        promise.complete( attempt )
      }
      def onFailure( response : JResponse, failure : Throwable ) : Unit = {
        promise.failure( failure )
      }
      def onComplete( result : JResult ) {
        if ( ! promise.isCompleted ) { // usually should have been completed already!
          val oops = {
            if ( result.isFailed ) result.getFailure() else new Exception( s"Unknown failure while executing jsonrpc request, method name: '${methodName}', params: '${paramsArray}'" )
          }
          promise.failure( oops )
        }
      }
    }

    request.send( handler )

    promise.future
  }

  def close() : Unit = () // nothing to do at this level
}

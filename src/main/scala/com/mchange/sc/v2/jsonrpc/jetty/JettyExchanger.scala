package com.mchange.sc.v2.jsonrpc.jetty

import com.mchange.sc.v2.jsonrpc._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v1.log.MLevel._

import com.mchange.v3.nio.ByteBufferUtils

import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.{Request => JRequest, Response => JResponse, Result => JResult}
import org.eclipse.jetty.client.util.{ByteBufferContentProvider, InputStreamResponseListener}
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.util.HttpCookieStore

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.ByteBuffer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

import play.api.libs.json._

object JettyExchanger {
  private implicit lazy val logger = mlogger( this )

  object Factory {
    def commonBuildClient() : HttpClient = {
      val httpClient = new HttpClient()
      httpClient.setFollowRedirects(false)
      httpClient.setCookieStore(new HttpCookieStore.Empty())
      httpClient
    }
    private [jsonrpc] def defaultInstanceBuildClient() : HttpClient = {
      val threadPool = new QueuedThreadPool()
      threadPool.setName( s"jsonrpc.Exchanger.Default[Jetty-HttpClient]" )
      threadPool.setDaemon( true )

      val client = commonBuildClient()
      client.setExecutor( threadPool )

      client
    }
  }
  final class Factory( buildClient : () => HttpClient = Factory.commonBuildClient _ ) extends Exchanger.Factory.Async {
    private [JettyExchanger] val httpClient = buildClient()
    httpClient.start()

    def apply( url : URL ) : Exchanger = new JettyExchanger( url, this )

    def close() : Unit = {
      httpClient.stop()
    }
  }
}
class JettyExchanger( url : URL, factory : JettyExchanger.Factory ) extends Exchanger {
  def exchange( methodName : String, paramsArray : JsArray )( implicit ec : ExecutionContext ) : Future[Response] = {
    val id = newRandomId()

    val paramsBytes = requestBytes( id, methodName, paramsArray )

    val byteBuffer = ByteBuffer.wrap( paramsBytes )

    val contentProvider = new ByteBufferContentProvider( "application/json", byteBuffer )

    val request = factory.httpClient.POST( url.toURI ).header( "charset", "utf-8" ).content( contentProvider )

    val promise = Promise[Response]()

    val handler = new JResponse.Listener.Adapter {

      override def onContent( response : JResponse, content : ByteBuffer ) : Unit = {
        val status = response.getStatus()
        if (status == 200) {
          val attempt = Try( traceParse( ByteBufferUtils.newArray( content ) ).as[Response] ensuring goodId( id ) )
          promise.complete( attempt )
        } else {
          promise.failure( new Exception( s"Unexpected HTTP status: ${status}" ) )
        }
      }
      override def onFailure( response : JResponse, failure : Throwable ) : Unit = {
        promise.failure( failure )
      }
      override def onComplete( result : JResult ) {
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


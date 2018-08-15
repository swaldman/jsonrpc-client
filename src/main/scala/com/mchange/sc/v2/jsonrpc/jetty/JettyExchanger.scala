package com.mchange.sc.v2.jsonrpc.jetty

import com.mchange.sc.v2.jsonrpc._

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v2.yinyang._

import com.mchange.sc.v1.log.MLevel._

import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.{Request => JRequest, Response => JResponse, Result => JResult}
import org.eclipse.jetty.client.util.{ByteBufferContentProvider, InputStreamResponseListener}
import org.eclipse.jetty.util.thread.{QueuedThreadPool, ScheduledExecutorScheduler}
import org.eclipse.jetty.util.HttpCookieStore
import org.eclipse.jetty.util.ssl.SslContextFactory

import java.util.concurrent.TimeUnit
import java.io.{ByteArrayInputStream,ByteArrayOutputStream}
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{Channels,WritableByteChannel}

import scala.concurrent.{ExecutionContext, Future, Promise}

import play.api.libs.json._

object JettyExchanger {
  private implicit lazy val logger = mlogger( this )

  val DefaultBufferSize = 32 * 1024

  object Factory {
    def commonBuildClient() : HttpClient = {
      val httpClient = new HttpClient(new SslContextFactory())
      httpClient.setFollowRedirects(false)
      httpClient.setCookieStore(new HttpCookieStore.Empty())
      httpClient
    }
    private [jsonrpc] def defaultInstanceBuildClient() : HttpClient = {
      val threadName = "jsonrpc.Exchanger.Default[Jetty-HttpClient]"

      val threadPool = new QueuedThreadPool()
      threadPool.setName( threadName )
      threadPool.setDaemon( true )

      val scheduler = new ScheduledExecutorScheduler( threadName + "-scheduler", true ); // the scheduler should also use daemon threads

      val client = commonBuildClient()
      client.setExecutor( threadPool )
      client.setScheduler( scheduler )

      client
    }
  }
  final class Factory( buildClient : () => HttpClient = Factory.commonBuildClient _ ) extends Exchanger.Factory.Async {
    private [JettyExchanger] val httpClient = buildClient()
    httpClient.start()

    def apply( config : Exchanger.Config ) : Exchanger = new JettyExchanger( config, this )

    def close() : Unit = {
      httpClient.stop()
    }
  }
}
class JettyExchanger( val config : Exchanger.Config, factory : JettyExchanger.Factory ) extends Exchanger {

  import JettyExchanger._

  def exchange( methodName : String, paramsArray : JsArray )( implicit ec : ExecutionContext ) : Future[Response] = {
    val id = newRandomId()

    val paramsBytes = traceRequestBytes( id, methodName, paramsArray )

    val byteBuffer = ByteBuffer.wrap( paramsBytes )

    val contentProvider = new ByteBufferContentProvider( "application/json", byteBuffer )

    val request = {
      config.finiteTimeoutMillis match {
        case Some( msecs ) => factory.httpClient.POST( config.httpUrl.toURI ).timeout( msecs, TimeUnit.MILLISECONDS ).header( "charset", "utf-8" ).content( contentProvider )
        case None          => factory.httpClient.POST( config.httpUrl.toURI ).header( "charset", "utf-8" ).content( contentProvider )
      }
    }

    val promise = Promise[Response]()

    val handler = new JResponse.Listener.Adapter {

      //MT: protected by this' lock
      var buffer  : ByteArrayOutputStream = null
      var channel : WritableByteChannel  = null

      private def ensureBuffer( response : JResponse ) : Unit = this.synchronized {
        if ( buffer == null ) {
          val status = response.getStatus()
          if (status == 200) {
            val lenStr = response.getHeaders().get("Content-Length")
            if ( lenStr != null ) {
              this.buffer = new ByteArrayOutputStream( lenStr.toInt )
            }
            else {
              this.buffer = new ByteArrayOutputStream( DefaultBufferSize )
            }
            this.channel = Channels.newChannel( buffer )
          } else {
            throw new Exception( s"Unexpected HTTP status: ${status}" )
          }
        }
      }
      private def appendToBuffer( partialContent : ByteBuffer ) : Unit = this.synchronized {
        channel.write( partialContent )
      }
      private def retrieveBuffer() : Array[Byte] = this.synchronized {
        channel.close()
        buffer.toByteArray
      }

      override def onContent( response : JResponse, content : ByteBuffer ) : Unit = {
        try {
          ensureBuffer( response )
          appendToBuffer( content )
        }
        catch {
          case t : Throwable => promise.failure( t )
        }
      }

      override def onComplete( result : JResult ) {
        if ( result.isFailed ) {
          promise.failure( result.getFailure() )
        }
        else {
          val attempt = traceAttemptParseResponse( id, retrieveBuffer() )
          promise.complete( attempt )
        }
      }
    }

    request.send( handler )

    promise.future
  }

  def close() : Unit = () // nothing to do at this level
}


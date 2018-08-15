package com.mchange.sc.v2

import play.api.libs.json._

import com.mchange.sc.v2.yinyang._

package object jsonrpc extends YinYang.YangBias.Base[Response.Error]( Response.Error.Empty ) {
  final object JsonrpcException {
    def stringifyErrorData( data : JsValue ) : String = {
      data match {
        case JsArray( elems ) => elems.map( stringifyErrorData ).mkString("\n","\n","")
        case str : JsString   => str.as[String]
        case JsNull           => "No further information"
        case whatevs          => Json.stringify( whatevs )
      }
    }
    def stringifyErrorData( data : Option[JsValue] ) : String = {
      data match {
        case Some( jsv ) => stringifyErrorData( jsv )
        case None        => "No further information"
      }
    }
  }
  final class JsonrpcException( val code : Int, val message : String, val data : Option[JsValue] = None ) extends Exception( s"${message} [code=${code}]: ${JsonrpcException.stringifyErrorData(data)}" ) {
    def this( errorResponse : Response.Error ) = this( errorResponse.error.code, errorResponse.error.message, errorResponse.error.data ) 
  }
  final class UnexpectedHttpStatusException( val status : Int, val reason : String ) extends Exception( s"Unexpected HTTP Status Code: ${status}, Reason: ${reason}" ) 

  type Response = YinYang[Response.Error,Response.Success]

  implicit val SuccessResponseFormat = Json.format[Response.Success]

  implicit val ErrorReportFormat   = Json.format[Response.Error.Report]
  implicit val ErrorResponseFormat = Json.format[Response.Error]

  implicit val ResponseFormat : Format[Response] = new Format[Response] {
    def reads( jsv : JsValue ) : JsResult[Response] = {
      jsv match {
        case jso : JsObject if jso.keys("result") => SuccessResponseFormat.reads( jso ).map( Yang(_) )
        case jso : JsObject if jso.keys("error")  => ErrorResponseFormat.reads( jso ).map( Yin(_) )
        case jso : JsObject                       => JsError( s"Response is expected to contain either a 'result' or 'error' field" )
        case _                                    => JsError( s"Response is expected as a JsObject, found ${jsv}" )
      }
    }
    def writes( response : Response ) : JsValue = response match {
      case Yin( errorResponse ) => ErrorResponseFormat.writes( errorResponse )
      case Yang( goodResponse ) => SuccessResponseFormat.writes( goodResponse )
    }
  }
}

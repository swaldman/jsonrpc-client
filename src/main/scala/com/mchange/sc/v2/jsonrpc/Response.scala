package com.mchange.sc.v2.jsonrpc

import play.api.libs.json.{JsNull,JsValue}

object Response {
  object Error {
    val Empty = Error(None, Report(0, "The result failed to match a filter or pattern and so is empty."))

    final case class Report( code : Int, message : String, data : Option[JsValue] = None)
  }
  final case class Error( id : Option[Int], error : Error.Report ) { // id won't be present in the error case where ID was not sent
    def vomit : Nothing = throw new JsonrpcException( this )
  }
  final case class Success( id : Int, result : JsValue );
}
// type Response = YinYang[Response.Error,Response.Success]
//
// defined in package object since not legal at top level



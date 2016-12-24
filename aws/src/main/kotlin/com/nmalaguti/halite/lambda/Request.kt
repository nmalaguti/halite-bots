package com.nmalaguti.halite.lambda

/*
{
    "resource": "Resource path",
    "path": "Path parameter",
    "httpMethod": "Incoming request's method name"
    "headers": {Incoming request headers}
    "queryStringParameters": {query string parameters }
    "pathParameters":  {path parameters}
    "stageVariables": {Applicable stage variables}
    "requestContext": {Request context, including authorizer-returned key-value pairs}
    "body": "A JSON string of the request payload."
    "isBase64Encoded": "A boolean flag to indicate if the applicable request payload is Base64-encode"
}
 */
class Request(val path: String,
              val httpMethod: String,
              val pathParameters: Map<String, String>,
              val queryStringParameters: Map<String, String>?,
              val body: String,
              val isBase64Encoded: Boolean)

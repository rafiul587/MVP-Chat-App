package com.example.mvpchatapplication.data


sealed class Response<out R> {
    data class Success<out T>(val data: T) : Response<T>()
    class Error(val error: Throwable) : Response<Nothing>()
}

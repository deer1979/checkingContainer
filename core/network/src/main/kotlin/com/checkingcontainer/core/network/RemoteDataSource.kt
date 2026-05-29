package com.checkingcontainer.core.network

interface RemoteDataSource {
    val isConnected: Boolean
    val backendDescription: String
}

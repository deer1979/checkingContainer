package com.checkingcontainer.core.network

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    val firestore: FirebaseFirestore,
) : RemoteDataSource {
    override val isConnected: Boolean = true
    override val backendDescription: String = "Firebase Firestore"
}

package com.example.sonicwavev4.network

import com.google.gson.annotations.SerializedName

data class CustomerCreationResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("message") val message: String
)

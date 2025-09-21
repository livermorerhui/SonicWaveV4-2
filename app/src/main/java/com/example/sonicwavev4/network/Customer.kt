package com.example.sonicwavev4.network

import com.google.gson.annotations.SerializedName

data class Customer(
    @SerializedName("name") val name: String,
    @SerializedName("dateOfBirth") val dateOfBirth: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("email") val email: String,
    @SerializedName("height") val height: Double,
    @SerializedName("weight") val weight: Double
)

package com.example.sonicwavev4.network

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Customer(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String,
    @SerializedName("dateOfBirth") val dateOfBirth: String?,
    @SerializedName("gender") val gender: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("email") val email: String,
    @SerializedName("height") val height: Double,
    @SerializedName("weight") val weight: Double
) : Parcelable

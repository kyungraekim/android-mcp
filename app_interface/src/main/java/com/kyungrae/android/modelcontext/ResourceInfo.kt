package com.kyungrae.android.modelcontext

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable data class for resource information, similar to MCP Resource
 */
data class ResourceInfo(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeString(mimeType)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ResourceInfo> {
        override fun createFromParcel(parcel: Parcel): ResourceInfo {
            return ResourceInfo(parcel)
        }

        override fun newArray(size: Int): Array<ResourceInfo?> {
            return arrayOfNulls(size)
        }
    }
}
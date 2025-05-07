package com.kyungrae.android.modelcontext

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable data class for tool information, similar to MCP Tool
 */
data class ToolInfo(
    val name: String,
    val description: String,
    val inputSchema: String, // JSON schema for input parameters as a string
    val isError: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt() == 1
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeString(inputSchema)
        parcel.writeInt(if (isError) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ToolInfo> {
        override fun createFromParcel(parcel: Parcel): ToolInfo {
            return ToolInfo(parcel)
        }

        override fun newArray(size: Int): Array<ToolInfo?> {
            return arrayOfNulls(size)
        }
    }
}
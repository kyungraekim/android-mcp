package com.kyungrae.android.modelcontext

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable data class for content, similar to MCP PromptMessageContent
 */
data class ContentItem(
    val type: String, // "text", "image", "resource", etc.
    val content: String, // Text content or base64-encoded data
    val mimeType: String? = null, // For non-text content
    val isError: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readInt() == 1
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        parcel.writeString(content)
        parcel.writeString(mimeType)
        parcel.writeInt(if (isError) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ContentItem> {
        override fun createFromParcel(parcel: Parcel): ContentItem {
            return ContentItem(parcel)
        }

        override fun newArray(size: Int): Array<ContentItem?> {
            return arrayOfNulls(size)
        }

        // Utility methods to create common content types
        fun createTextContent(text: String, isError: Boolean = false): ContentItem {
            return ContentItem("text", text, "text/plain", isError)
        }

        fun createImageContent(base64Data: String, mimeType: String): ContentItem {
            return ContentItem("image", base64Data, mimeType)
        }

        fun createResourceContent(resourceText: String, uri: String, mimeType: String): ContentItem {
            return ContentItem("resource", resourceText, mimeType)
        }

        fun createErrorContent(errorMessage: String): ContentItem {
            return ContentItem("text", errorMessage, "text/plain", true)
        }
    }
}
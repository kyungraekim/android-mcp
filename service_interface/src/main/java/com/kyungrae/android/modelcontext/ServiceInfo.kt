package com.kyungrae.android.modelcontext

import android.os.Parcel
import android.os.Parcelable

/**
 * 서비스 정보를 담는 Parcelable 데이터 클래스 (Parcelize 미사용)
 */
// data class를 사용하여 equals(), hashCode(), toString() 자동 생성을 활용합니다.
data class ServiceInfo(
    val packageName: String,
    val className: String,
    val serviceType: String
) : Parcelable { // Parcelable 인터페이스 구현

    // Parcel로부터 객체를 생성하는 보조 생성자 (Java의 Parcel 생성자와 동일 역할)
    // readString()은 nullable String?을 반환할 수 있으므로,
    // non-null 타입인 주 생성자의 파라미터에 맞추기 위해 elvis 연산자(?:)로 기본값("")을 제공합니다.
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    // 객체의 내용을 Parcel에 쓰는 메서드
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeString(className)
        parcel.writeString(serviceType)
    }

    // Parcelable 객체의 특별한 내용을 설명 (일반적으로 0 반환)
    override fun describeContents(): Int {
        return 0
    }

    // companion object는 Java의 static 멤버와 유사한 역할을 합니다.
    // 여기에 CREATOR를 구현합니다.
    companion object CREATOR : Parcelable.Creator<ServiceInfo> {
        // Parcel로부터 ServiceInfo 객체를 생성
        override fun createFromParcel(parcel: Parcel): ServiceInfo {
            return ServiceInfo(parcel) // 위에서 정의한 보조 생성자 호출
        }

        // 지정된 크기의 ServiceInfo 객체 배열을 생성
        override fun newArray(size: Int): Array<ServiceInfo?> {
            return arrayOfNulls(size)
        }
    }

    // data class가 자동으로 equals(), hashCode(), toString() 메서드를 생성해줍니다.
    // Java 코드에서 직접 구현했던 부분과 동일한 기능을 제공합니다.
}
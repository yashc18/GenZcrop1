package com.nachiket.genzcrop_farmers.data_class

data class Crop(
    var name: String = "",
    val variety: String = "",
    val grade: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val ready_date: String = "",
    val stage: Int = 0,
    val image1: String = ""
)
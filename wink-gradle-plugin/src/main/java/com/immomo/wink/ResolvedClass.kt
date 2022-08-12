package com.immomo.wink

import java.io.Serializable

data class ResolvedClass(
    val className: String,
//    val superName: String,
//    val interfaces: Set<String>,
//    val resoluedBy: Set<String>,
//    val resolvedTo: Set<String>
    val constKV: MutableMap<String, String?> = mutableMapOf<String,String?>()
//    val constValues:MutableSet<Any> = mutableSetOf()
) : Serializable{
    private val serialVersionUID = 1L
}
